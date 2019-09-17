package com.archos.mediacenter.video.leanback.wizard;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import android.view.View;
import android.util.SparseArray;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.utils.SubtitlesWizardCommon;

import java.util.ArrayList;
import java.util.List;

public class SubtitlesWizardFragment extends GuidedStepSupportFragment {

    public static final int NO_FILE_MESSAGE_ID = 100;
    public static final int CURRENT_TITLE_ID = 200;
    public static final int NO_CURRENT_MESSAGE_ID = 300;
    public static final int AVAILABLE_TITLE_ID = 400;
    public static final int NO_AVAILABLE_MESSAGE_ID = 500;

    private int mId = 600;
    private SparseArray<FileData> mFiles = new SparseArray<FileData>();
    private SparseArray<ActionData> mActions = new SparseArray<ActionData>();

    private Overlay mOverlay;

    private SubtitlesWizardCommon mWizardCommon;

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        mWizardCommon = new SubtitlesWizardCommon(getActivity());
        
        mWizardCommon.onCreate();

        actions.addAll(createActions());
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.get_subtitles_on_drive),
                mWizardCommon.getHelpMessage(), "",
                ContextCompat.getDrawable(getActivity(),R.drawable.filetype_new_subtitles));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView(){
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        mOverlay.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    @Override
    public boolean onSubGuidedActionClicked(GuidedAction action) {
        int actionId = (int)action.getId();
        ActionData actionData = mActions.get(actionId);

        if (actionData != null) {
            FileData fileData = mFiles.get(actionData.fileId);

            if (fileData != null) {
                if (actionData.delete) {
                    boolean fileDeleted = mWizardCommon.deleteFile(fileData.path, fileData.index, fileData.current);

                    if (fileDeleted) {
                        setActions(createActions());
                        getActivity().setResult(Activity.RESULT_OK);

                        return true;
                    }
                }
                else {
                    boolean fileRenamed = mWizardCommon.renameFile(fileData.path, fileData.index);

                    if (fileRenamed) {
                        setActions(createActions());
                        getActivity().setResult(Activity.RESULT_OK);

                        return true;
                    }
                }
            }
        }

        return false;
    }

    private List<GuidedAction> createActions() {
        List<GuidedAction> actions = new ArrayList<>();

        mActions.clear();
        mFiles.clear();

        if (mWizardCommon.getCurrentFilesCount() == 0 && mWizardCommon.getAvailableFilesCount() == 0) {
            actions.add(new GuidedAction.Builder(getActivity())
                .id(NO_FILE_MESSAGE_ID)
                .title(getString(R.string.subtitles_wizard_no_files))
                .multilineDescription(true)
                .focusable(false)
                .build());
        }
        else {
            actions.add(new GuidedAction.Builder(getActivity())
                .id(CURRENT_TITLE_ID)
                .icon(ContextCompat.getDrawable(getActivity(), R.drawable.wizard_dot))
                .title(getString(R.string.subtitles_wizard_current_files))
                .focusable(false)
                .build());

            if (mWizardCommon.getCurrentFilesCount() == 0) {
                actions.add(new GuidedAction.Builder(getActivity())
                    .id(NO_CURRENT_MESSAGE_ID)
                    .title(getString(R.string.subtitles_wizard_empty_list))
                    .multilineDescription(true)
                    .infoOnly(true)
                    .build());
            }
            else {
                for (int i = 0; i < mWizardCommon.getCurrentFilesCount(); i++) {
                    int fileId = mId++;
                    List<GuidedAction> subActions = new ArrayList<>();

                    int deleteId = mId++;
                    ActionData deleteData = new ActionData(true, fileId);

                    mActions.put(deleteId, deleteData);
                    subActions.add(new GuidedAction.Builder(getActivity())
                        .id(deleteId)
                        .title(getString(R.string.subtitles_wizard_delete))
                        .build());

                    String path = mWizardCommon.getCurrentFile(i);
                    FileData fileData = new FileData(true, i, path);

                    mFiles.put(fileId, fileData);
                    actions.add(new GuidedAction.Builder(getActivity())
                        .id(fileId)
                        .title(mWizardCommon.getFileName(path))
                        .description(mWizardCommon.getFileSize(path) + (mWizardCommon.isCacheFile(path) ? " - " + getString(R.string.subtitles_wizard_cache) : ""))
                        .multilineDescription(true)
                        .subActions(subActions)
                        .build());
                }
            }

            actions.add(new GuidedAction.Builder(getActivity())
                .id(AVAILABLE_TITLE_ID)
                .icon(ContextCompat.getDrawable(getActivity(), R.drawable.wizard_dot))
                .title(getString(R.string.subtitles_wizard_available_files))
                .focusable(false)
                .build());

            if (mWizardCommon.getAvailableFilesCount() == 0) {
                actions.add(new GuidedAction.Builder(getActivity())
                    .id(NO_AVAILABLE_MESSAGE_ID)
                    .title(getString(R.string.subtitles_wizard_empty_list) + ". " + getString(R.string.subtitles_wizard_add_files))
                    .multilineDescription(true)
                    .infoOnly(true)
                    .build());
            }
            else {
                for (int i = 0; i < mWizardCommon.getAvailableFilesCount(); i++) {
                    int fileId = mId++;
                    List<GuidedAction> subActions = new ArrayList<>();

                    int associateId = mId++;
                    ActionData associateData = new ActionData(false, fileId);

                    mActions.put(associateId, associateData);
                    subActions.add(new GuidedAction.Builder(getActivity())
                        .id(associateId)
                        .title(getString(R.string.subtitles_wizard_associate))
                        .build());

                    int deleteId = mId++;
                    ActionData deleteData = new ActionData(true, fileId);

                    mActions.put(deleteId, deleteData);
                    subActions.add(new GuidedAction.Builder(getActivity())
                        .id(deleteId)
                        .title(getString(R.string.subtitles_wizard_delete))
                        .build());

                    String path = mWizardCommon.getAvailableFile(i);
                    FileData fileData = new FileData(false, i, path);

                    mFiles.put(fileId, fileData);
                    actions.add(new GuidedAction.Builder(getActivity())
                            .id(fileId)
                            .title(mWizardCommon.getFileName(path))
                            .description(mWizardCommon.getFileSize(path) + (mWizardCommon.isCacheFile(path) ? " - " + getString(R.string.subtitles_wizard_cache) : ""))
                            .multilineDescription(true)
                            .subActions(subActions)
                            .build());
                }
            }
        }

        return actions;
    }

    private class FileData {
        boolean current;
        int index;
        String path;

        public FileData(boolean c, int i, String p) {
            current = c;
            index = i;
            path = p;
        }
    }

    private class ActionData {
        boolean delete;
        int fileId;

        public ActionData(boolean d, int f) {
            delete = d;
            fileId = f;
        }
    }
}