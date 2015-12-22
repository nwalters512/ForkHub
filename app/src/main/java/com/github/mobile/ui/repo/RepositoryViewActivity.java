/*
 * Copyright 2012 GitHub Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mobile.ui.repo;

import com.google.inject.Inject;

import com.github.kevinsawicki.wishlist.ViewUtils;
import com.github.mobile.Intents.Builder;
import com.github.mobile.R;
import com.github.mobile.core.repo.ForkRepositoryTask;
import com.github.mobile.core.repo.RefreshRepositoryTask;
import com.github.mobile.core.repo.RepositoryUtils;
import com.github.mobile.core.repo.StarRepositoryTask;
import com.github.mobile.core.repo.StarredRepositoryTask;
import com.github.mobile.core.repo.UnstarRepositoryTask;
import com.github.mobile.ui.TabPagerActivity;
import com.github.mobile.ui.user.UriLauncherActivity;
import com.github.mobile.ui.user.UserViewActivity;
import com.github.mobile.util.AvatarLoader;
import com.github.mobile.util.ShareUtils;
import com.github.mobile.util.ToastUtils;
import com.github.mobile.util.TypefaceUtils;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static com.github.mobile.Intents.EXTRA_POSITION;
import static com.github.mobile.Intents.EXTRA_REPOSITORY;
import static com.github.mobile.ResultCodes.RESOURCE_CHANGED;
import static com.github.mobile.ui.repo.RepositoryPagerAdapter.ITEM_CODE;

/**
 * Activity to view a repository
 */
public class RepositoryViewActivity extends TabPagerActivity<RepositoryPagerAdapter> {

    /**
     * Create intent for this activity
     *
     * @param repository
     * @return intent
     */
    public static Intent createIntent(Repository repository) {
        return new Builder("repo.VIEW").repo(repository).toIntent();
    }

    /**
     * Create intent for this activity and open the issues tab
     *
     * @param repository
     * @return intent
     */
    public static Intent createIntentForIssues(Repository repository) {
        return new Builder("repo.VIEW").repo(repository).add(EXTRA_POSITION, 3).toIntent();
    }

    private Repository repository;

    @Inject
    private AvatarLoader avatars;

    private ProgressBar loadingBar;

    private boolean isStarred;

    private boolean starredStatusChecked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = getSerializableExtra(EXTRA_REPOSITORY);

        loadingBar = finder.find(R.id.pb_loading);

        User owner = repository.getOwner();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(repository.getName());
        actionBar.setSubtitle(owner.getLogin());
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (owner.getAvatarUrl() != null && RepositoryUtils.isComplete(repository))
            configurePager();
        else {
            avatars.bind(getSupportActionBar(), owner);
            ViewUtils.setGone(loadingBar, false);
            setGone(true);
            new RefreshRepositoryTask(this, repository) {

                @Override
                protected void onSuccess(Repository fullRepository) throws Exception {
                    super.onSuccess(fullRepository);

                    repository = fullRepository;
                    getIntent().putExtra(EXTRA_REPOSITORY, repository);
                    configurePager();
                }

                @Override
                protected void onException(Exception e) throws RuntimeException {
                    super.onException(e);

                    ToastUtils.show(RepositoryViewActivity.this, R.string.error_repo_load);
                    ViewUtils.setGone(loadingBar, true);
                }
            }.execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu optionsMenu) {
        getMenuInflater().inflate(R.menu.repository, optionsMenu);
        return super.onCreateOptionsMenu(optionsMenu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem followItem = menu.findItem(R.id.m_star);

        followItem.setVisible(starredStatusChecked);
        followItem.setTitle(isStarred ? R.string.unstar : R.string.star);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onSearchRequested() {
        if (pager.getCurrentItem() == 1) {
            Bundle args = new Bundle();
            args.putSerializable(EXTRA_REPOSITORY, repository);
            startSearch(null, false, args, false);
            return true;
        } else
            return false;
    }

    @Override
    public void onBackPressed() {
        if (adapter == null || pager.getCurrentItem() != ITEM_CODE || !adapter.onBackPressed())
            super.onBackPressed();
    }

    private void configurePager() {
        avatars.bind(getSupportActionBar(), repository.getOwner());
        configureTabPager();
        ViewUtils.setGone(loadingBar, true);
        setGone(false);
        checkStarredRepositoryStatus();
        int initialPosition = getIntExtra(EXTRA_POSITION);
        if (initialPosition != -1) {
            pager.setItem(initialPosition);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.m_star:
            starRepository();
            return true;
        case R.id.m_fork:
            forkRepository();
            return true;
        case R.id.m_contributors:
            startActivity(RepositoryContributorsActivity.createIntent(repository));
            return true;
        case R.id.m_share:
            shareRepository();
            return true;
        case R.id.m_refresh:
            checkStarredRepositoryStatus();
            return super.onOptionsItemSelected(item);
        case android.R.id.home:
            finish();
            Intent intent = UserViewActivity.createIntent(repository.getOwner());
            intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDialogResult(int requestCode, int resultCode, Bundle arguments) {
        adapter.onDialogResult(pager.getCurrentItem(), requestCode, resultCode, arguments);
    }

    @Override
    protected RepositoryPagerAdapter createAdapter() {
        return new RepositoryPagerAdapter(this, repository.isHasIssues());
    }

    @Override
    protected int getContentView() {
        return R.layout.tabbed_progress_pager;
    }

    @Override
    protected String getIcon(int position) {
        switch (position) {
        case 0:
            return TypefaceUtils.ICON_RSS;
        case 1:
            return TypefaceUtils.ICON_FILE_CODE;
        case 2:
            return TypefaceUtils.ICON_GIT_COMMIT;
        case 3:
            return TypefaceUtils.ICON_ISSUE_OPENED;
        default:
            return super.getIcon(position);
        }
    }

    private void starRepository() {
        if (isStarred)
            new UnstarRepositoryTask(this, repository) {

                @Override
                protected void onSuccess(Void v) throws Exception {
                    super.onSuccess(v);

                    isStarred = !isStarred;
                    setResult(RESOURCE_CHANGED);
                }

                @Override
                protected void onException(Exception e) throws RuntimeException {
                    super.onException(e);

                    ToastUtils.show(RepositoryViewActivity.this, R.string.error_unstarring_repository);
                }
            }.start();
        else
            new StarRepositoryTask(this, repository) {

                @Override
                protected void onSuccess(Void v) throws Exception {
                    super.onSuccess(v);

                    isStarred = !isStarred;
                    setResult(RESOURCE_CHANGED);
                }

                @Override
                protected void onException(Exception e) throws RuntimeException {
                    super.onException(e);

                    ToastUtils.show(RepositoryViewActivity.this, R.string.error_starring_repository);
                }
            }.start();
    }

    private void checkStarredRepositoryStatus() {
        starredStatusChecked = false;
        new StarredRepositoryTask(this, repository) {

            @Override
            protected void onSuccess(Boolean watching) throws Exception {
                super.onSuccess(watching);

                isStarred = watching;
                starredStatusChecked = true;
                invalidateOptionsMenu();
            }
        }.execute();
    }

    private void shareRepository() {
        String repoUrl = repository.getHtmlUrl();
        if (TextUtils.isEmpty(repoUrl))
            repoUrl = "https://github.com/" + repository.generateId();
        Intent sharingIntent = ShareUtils.create(repository.generateId(), repoUrl);
        startActivity(sharingIntent);
    }

    private void forkRepository() {
        new ForkRepositoryTask(this, repository) {

            @Override
            protected void onSuccess(Repository e) throws Exception {
                super.onSuccess(e);

                if (e != null) {
                    UriLauncherActivity.launchUri(getContext(), Uri.parse(e.getHtmlUrl()));
                } else {
                    ToastUtils.show(RepositoryViewActivity.this, R.string.error_forking_repository);
                }
            }

            @Override
            protected void onException(Exception e) throws RuntimeException {
                super.onException(e);

                ToastUtils.show(RepositoryViewActivity.this, R.string.error_forking_repository);
            }
        }.start();
    }
}
