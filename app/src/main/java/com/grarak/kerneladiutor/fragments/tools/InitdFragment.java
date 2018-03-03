/*
 * Copyright (C) 2015-2016 Willi Ye <williye97@gmail.com>
 *
 * This file is part of Kernel Adiutor.
 *
 * Kernel Adiutor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kernel Adiutor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Kernel Adiutor.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.grarak.kerneladiutor.fragments.tools;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.Menu;

import com.grarak.kerneladiutor.R;
import com.grarak.kerneladiutor.activities.EditorActivity;
import com.grarak.kerneladiutor.fragments.RecyclerViewFragment;
import com.grarak.kerneladiutor.fragments.SwitcherFragment;
import com.grarak.kerneladiutor.utils.Prefs;
import com.grarak.kerneladiutor.utils.Utils;
import com.grarak.kerneladiutor.utils.ViewUtils;
import com.grarak.kerneladiutor.utils.root.RootUtils;
import com.grarak.kerneladiutor.utils.tools.Initd;
import com.grarak.kerneladiutor.views.dialog.Dialog;
import com.grarak.kerneladiutor.views.recyclerview.CardView;
import com.grarak.kerneladiutor.views.recyclerview.DescriptionView;
import com.grarak.kerneladiutor.views.recyclerview.RecyclerViewItem;

import java.util.List;

/**
 * Created by willi on 16.07.16.
 */
public class InitdFragment extends RecyclerViewFragment {

    private Dialog mExecuteDialog;
    private Dialog mResultDialog;
    private Dialog mDeleteDialog;
    private boolean mShowCreateNameDialog;

    private String mCreateName;

    private String mEditInitd;

    @Override
    protected Drawable getTopFabDrawable() {
        Drawable drawable = DrawableCompat.wrap(
                ContextCompat.getDrawable(getActivity(), R.drawable.ic_add));
        DrawableCompat.setTint(drawable, Color.WHITE);
        return drawable;
    }

    @Override
    protected boolean showTopFab() {
        return true;
    }

    @Override
    protected void init() {
        super.init();

        addViewPagerFragment(SwitcherFragment.newInstance(
                getString(R.string.emulate_initd),
                getString(R.string.emulate_initd_summary),
                Prefs.getBoolean("initd_onboot", false, getActivity()),
                (compoundButton, b) -> Prefs.saveBoolean("initd_onboot", b, getActivity())));

        if (mExecuteDialog != null) {
            mExecuteDialog.show();
        }
        if (mResultDialog != null) {
            mResultDialog.show();
        }
        if (mDeleteDialog != null) {
            mDeleteDialog.show();
        }
        if (mShowCreateNameDialog) {
            showCreateDialog();
        }
    }

    @Override
    protected void addItems(List<RecyclerViewItem> items) {
        load(items);
    }

    private void reload() {
        if (!isReloading()) {
            getHandler().postDelayed(() -> {
                clearItems();
                reload(new ReloadHandler<>());
            }, 250);
        }
    }

    @Override
    protected void load(List<RecyclerViewItem> items) {
        super.load(items);

        for (final String initd : Initd.list()) {
            CardView cardView = new CardView(getActivity());
            cardView.setOnMenuListener((cardView1, popupMenu) -> {
                Menu menu = popupMenu.getMenu();
                menu.add(Menu.NONE, 0, Menu.NONE, getString(R.string.edit));
                menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.delete));

                popupMenu.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case 0:
                            mEditInitd = initd;
                            Intent intent = new Intent(getActivity(), EditorActivity.class);
                            intent.putExtra(EditorActivity.TITLE_INTENT, initd);
                            intent.putExtra(EditorActivity.TEXT_INTENT, Initd.read(initd));
                            startActivityForResult(intent, 0);
                            break;
                        case 1:
                            mDeleteDialog = ViewUtils.dialogBuilder(getString(R.string.sure_question),
                                    (dialogInterface, i) -> {
                                    },
                                    (dialogInterface, i) -> {
                                        Initd.delete(initd);
                                        reload();
                                    },
                                    dialogInterface -> mDeleteDialog = null, getActivity());
                            mDeleteDialog.show();
                            break;
                    }
                    return false;
                });
            });

            DescriptionView descriptionView = new DescriptionView();
            descriptionView.setSummary(initd);
            descriptionView.setOnItemClickListener(item -> {
                mExecuteDialog = ViewUtils.dialogBuilder(getString(R.string.exceute_question, initd),
                        (dialogInterface, i) -> {
                        },
                        (dialogInterface, i) -> execute(initd),
                        dialogInterface -> mExecuteDialog = null,
                        getActivity());
                mExecuteDialog.show();
            });

            cardView.addItem(descriptionView);
            items.add(cardView);
        }
    }

    private void execute(final String initd) {
        new ExecuteTask(getActivity(), initd).execute(this);
    }

    private static class ExecuteTask extends AsyncTask<InitdFragment, Void, InitdFragment> {

        private ProgressDialog mProgressDialog;
        private String mInitd;
        private String mResult;

        private ExecuteTask(Context context, String initd) {
            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setMessage(context.getString(R.string.executing));
            mProgressDialog.setCancelable(false);

            mInitd = initd;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
        }

        @Override
        protected InitdFragment doInBackground(InitdFragment... initdFragments) {
            mResult = Initd.execute(mInitd);
            return initdFragments[0];
        }

        @Override
        protected void onPostExecute(InitdFragment initdFragment) {
            super.onPostExecute(initdFragment);
            try {
                mProgressDialog.dismiss();
            } catch (IllegalArgumentException ignored) {
            }
            if (mResult != null && !mResult.isEmpty()) {
                initdFragment.mResultDialog = ViewUtils.dialogBuilder(mResult,
                        null,
                        null,
                        dialogInterface -> initdFragment.mResultDialog = null,
                        initdFragment.getActivity()).setTitle(
                        initdFragment.getString(R.string.result));
                initdFragment.mResultDialog.show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null) return;
        if (requestCode == 0) {
            Initd.write(mEditInitd, data.getCharSequenceExtra(EditorActivity.TEXT_INTENT).toString());
            reload();
        } else if (requestCode == 1) {
            Initd.write(mCreateName, data.getCharSequenceExtra(EditorActivity.TEXT_INTENT).toString());
            mCreateName = null;
            reload();
        }
    }

    @Override
    protected void onTopFabClick() {
        super.onTopFabClick();

        showCreateDialog();
    }

    private void showCreateDialog() {
        mShowCreateNameDialog = true;
        ViewUtils.dialogEditText(null,
                (dialogInterface, i) -> {
                },
                text -> {
                    if (text.isEmpty()) {
                        Utils.toast(R.string.name_empty, getActivity());
                        return;
                    }

                    if (Initd.list().indexOf(text) > -1) {
                        Utils.toast(getString(R.string.already_exists, text), getActivity());
                        return;
                    }

                    mCreateName = text;
                    Intent intent = new Intent(getActivity(), EditorActivity.class);
                    intent.putExtra(EditorActivity.TITLE_INTENT, mCreateName);
                    intent.putExtra(EditorActivity.TEXT_INTENT, "#!/system/bin/sh\n\n");
                    startActivityForResult(intent, 1);
                }, getActivity()).setTitle(getString(R.string.name))
                .setOnDismissListener(
                        dialogInterface -> mShowCreateNameDialog = false).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RootUtils.mount(false, "/system");
    }

    @Override
    protected boolean showAd() {
        return true;
    }

}
