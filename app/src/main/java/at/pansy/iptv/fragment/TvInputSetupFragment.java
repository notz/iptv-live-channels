/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.pansy.iptv.fragment;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.SyncStatusObserver;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.widget.Toast;

import at.pansy.iptv.R;
import at.pansy.iptv.service.AccountService;
import at.pansy.iptv.util.IptvUtil;
import at.pansy.iptv.util.SyncUtil;
import at.pansy.iptv.util.TvContractUtil;
import at.pansy.iptv.xmltv.XmlTvParser;

/**
 * Fragment which shows a sample UI for registering channels and setting up SyncAdapter to
 * provide program information in the background.
 */
public class TvInputSetupFragment extends DetailsFragment {

    private static final int ACTION_ADD_CHANNELS = 1;
    private static final int ACTION_CANCEL = 2;
    private static final int ACTION_IN_PROGRESS = 3;

    private XmlTvParser.TvListing tvListing = null;
    private String inputId = null;

    private Action addChannelAction;
    private Action inProgressAction;
    private ArrayObjectAdapter adapter;
    private Object syncObserverHandle;
    private boolean syncRequested;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        inputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        new SetupRowTask().execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (syncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(syncObserverHandle);
            syncObserverHandle = null;
        }
    }

    private class SetupRowTask extends AsyncTask<Uri, String, Boolean> {

        @Override
        protected Boolean doInBackground(Uri... params) {
            tvListing = IptvUtil.getTvListings(getActivity(),
                    getString(R.string.iptv_ink_channel_url), IptvUtil.FORMAT_M3U);
            return tvListing != null;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                initUIs();
            } else {
                onError(R.string.feed_error_message);
            }
        }

        private void initUIs() {
            DetailsOverviewRowPresenter dorPresenter =
                    new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
            dorPresenter.setSharedElementEnterTransition(getActivity(), "SetUpFragment");

            addChannelAction = new Action(ACTION_ADD_CHANNELS, getResources().getString(
                    R.string.tv_input_setup_add_channel));
            Action cancelAction = new Action(ACTION_CANCEL,
                    getResources().getString(R.string.tv_input_setup_cancel));
            inProgressAction = new Action(ACTION_IN_PROGRESS, getResources().getString(
                    R.string.tv_input_setup_in_progress));

            DetailsOverviewRow row = new DetailsOverviewRow(tvListing);
            row.addAction(addChannelAction);
            row.addAction(cancelAction);

            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            // set detail background and style
            dorPresenter.setBackgroundColor(getResources().getColor(R.color.detail_background));
            dorPresenter.setStyleLarge(true);

            dorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
                @Override
                public void onActionClicked(Action action) {
                    if (action.getId() == ACTION_ADD_CHANNELS) {
                        setupChannels(inputId);
                    } else if (action.getId() == ACTION_CANCEL) {
                        getActivity().finish();
                    }
                }
            });

            presenterSelector.addClassPresenter(DetailsOverviewRow.class, dorPresenter);
            presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
            adapter = new ArrayObjectAdapter(presenterSelector);
            adapter.add(row);

            setAdapter(adapter);

            BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
            backgroundManager.attach(getActivity().getWindow());
            backgroundManager.setDrawable(
                        getActivity().getDrawable(R.drawable.default_background));
        }
    }

    private void onError(int errorResId) {
        Toast.makeText(getActivity(), errorResId, Toast.LENGTH_SHORT).show();
        getActivity().finish();
    }

    private void setupChannels(String inputId) {
        if (tvListing == null) {
            onError(R.string.feed_error_message);
            return;
        }

        TvContractUtil.updateChannels(getActivity(), inputId, tvListing.channels);
        SyncUtil.setUpPeriodicSync(getActivity(), inputId);
        SyncUtil.requestSync(inputId, true);

        syncRequested = true;
        // Watch for sync state changes
        if (syncObserverHandle == null) {
            final int mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING |
                    ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;
            syncObserverHandle = ContentResolver.addStatusChangeListener(mask,
                    mSyncStatusObserver);
        }
    }

    private class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {
        @Override
        protected void onBindDescription(ViewHolder viewHolder, Object item) {
            viewHolder.getTitle().setText(R.string.tv_input_label);
        }
    }

    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        private boolean syncServiceStarted;
        private boolean finished;

        @Override
        public void onStatusChanged(int which) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (finished) {
                        return;
                    }

                    Account account = AccountService.getAccount(SyncUtil.ACCOUNT_TYPE);
                    boolean syncActive = ContentResolver.isSyncActive(account,
                            TvContract.AUTHORITY);
                    boolean syncPending = ContentResolver.isSyncPending(account,
                            TvContract.AUTHORITY);
                    boolean syncServiceInProgress = syncActive || syncPending;
                    if (syncRequested && syncServiceStarted && !syncServiceInProgress) {
                        // Only current programs are registered at this point. Request a full sync.
                        SyncUtil.requestSync(inputId, false);

                        getActivity().setResult(Activity.RESULT_OK);
                        getActivity().finish();
                        finished = true;
                    }
                    if (!syncServiceStarted && syncServiceInProgress) {
                        syncServiceStarted = syncServiceInProgress;
                        DetailsOverviewRow detailRow = (DetailsOverviewRow) adapter.get(0);
                        detailRow.removeAction(addChannelAction);
                        detailRow.addAction(0, inProgressAction);
                        adapter.notifyArrayItemRangeChanged(0, 1);
                    }
                }
            });
        }
    };

}
