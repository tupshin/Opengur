package com.kenny.openimgur.activities;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.kenny.openimgur.R;
import com.kenny.openimgur.adapters.UploadPhotoAdapter;
import com.kenny.openimgur.api.ApiClient;
import com.kenny.openimgur.api.Endpoints;
import com.kenny.openimgur.api.ImgurBusEvent;
import com.kenny.openimgur.classes.ImgurTopic;
import com.kenny.openimgur.classes.PhotoUploadListener;
import com.kenny.openimgur.classes.Upload;
import com.kenny.openimgur.fragments.UploadEditDialogFragment;
import com.kenny.openimgur.fragments.UploadInfoFragment;
import com.kenny.openimgur.fragments.UploadLinkDialogFragment;
import com.kenny.openimgur.services.UploadService2;
import com.kenny.openimgur.ui.MultiStateView;
import com.kenny.openimgur.util.FileUtil;
import com.kenny.openimgur.util.LogUtil;
import com.kenny.snackbar.SnackBar;
import com.kenny.snackbar.SnackBarItem;
import com.kenny.snackbar.SnackBarListener;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.InjectView;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;

/**
 * Created by Kenny-PC on 6/20/2015.
 */
public class UploadActivity2 extends BaseActivity implements PhotoUploadListener {
    private static final int REQUEST_CODE_CAMERA = 123;

    private static final int REQUEST_CODE_GALLERY = 321;

    @InjectView(R.id.multiView)
    MultiStateView mMultiView;

    @InjectView(R.id.list)
    RecyclerView mRecyclerView;

    private UploadPhotoAdapter mAdapter;

    private File mTempFile;

    public static Intent createIntent(Context context) {
        return new Intent(context, UploadActivity2.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload2);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        new ItemTouchHelper(mSimpleItemTouchCallback).attachToRecyclerView(mRecyclerView);
        getSupportActionBar().setTitle(R.string.upload);
        checkForTopics();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.upload_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.upload);
        item.setVisible(mAdapter != null && !mAdapter.isEmpty());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.upload:
                getFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, UploadInfoFragment.newInstance(), UploadInfoFragment.class.getSimpleName())
                        .commit();
                getSupportActionBar().hide();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @OnClick({R.id.cameraBtn, R.id.linkBtn, R.id.galleryBtn})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.cameraBtn:
                mTempFile = FileUtil.createFile(FileUtil.EXTENSION_JPEG);

                if (FileUtil.isFileValid(mTempFile)) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTempFile));
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(intent, REQUEST_CODE_CAMERA);
                    } else {
                        SnackBar.show(this, R.string.cant_launch_intent);
                    }
                }
                break;

            case R.id.galleryBtn:
                startActivityForResult(PhotoPickerActivity.createInstance(getApplicationContext()), REQUEST_CODE_GALLERY);
                break;

            case R.id.linkBtn:
                getFragmentManager().beginTransaction()
                        .add(new UploadLinkDialogFragment(), UploadLinkDialogFragment.TAG)
                        .commit();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_GALLERY:
                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(PhotoPickerActivity.KEY_PHOTOS)) {
                    List<String> photos = data.getStringArrayListExtra(PhotoPickerActivity.KEY_PHOTOS);
                    List<Upload> uploads = new ArrayList<>(photos.size());

                    for (String s : photos) {
                        uploads.add(new Upload(s));
                    }

                    if (mAdapter == null) {
                        mAdapter = new UploadPhotoAdapter(this, uploads, this);
                        mRecyclerView.setAdapter(mAdapter);
                    } else {
                        mAdapter.addItems(uploads);
                    }

                    mMultiView.setViewState(MultiStateView.ViewState.CONTENT);
                    supportInvalidateOptionsMenu();
                }
                break;

            case REQUEST_CODE_CAMERA:
                if (resultCode == Activity.RESULT_OK && FileUtil.isFileValid(mTempFile)) {
                    FileUtil.scanFile(Uri.fromFile(mTempFile), getApplicationContext());
                    String fileLocation = mTempFile.getAbsolutePath();
                    Upload upload = new Upload(fileLocation);

                    if (mAdapter == null) {
                        List<Upload> uploadList = new ArrayList<>(1);
                        uploadList.add(upload);
                        mAdapter = new UploadPhotoAdapter(this, uploadList, this);
                        mRecyclerView.setAdapter(mAdapter);
                    } else {
                        mAdapter.addItem(upload);
                    }

                    mMultiView.setViewState(MultiStateView.ViewState.CONTENT);
                    mTempFile = null;
                    supportInvalidateOptionsMenu();
                } else {
                    SnackBar.show(this, R.string.upload_camera_error);
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected int getStyleRes() {
        return theme.isDarkTheme ? R.style.Theme_Not_Translucent_Dark : R.style.Theme_Not_Translucent_Light;
    }

    @Override
    public void onLinkAdded(String link) {
        Upload upload = new Upload(link, true);

        if (mAdapter == null) {
            List<Upload> uploadList = new ArrayList<>(1);
            uploadList.add(upload);
            mAdapter = new UploadPhotoAdapter(this, uploadList, this);
            mRecyclerView.setAdapter(mAdapter);
        } else {
            mAdapter.addItem(upload);
        }

        mMultiView.setViewState(MultiStateView.ViewState.CONTENT);
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onItemClicked(int position) {
        Upload upload = mAdapter.getItem(position);

        getFragmentManager().beginTransaction()
                .add(UploadEditDialogFragment.createInstance(upload), UploadEditDialogFragment.TAG)
                .commit();
    }

    @Override
    public void onItemEdited(Upload upload) {
        mAdapter.notifyDataSetChanged();
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onUpload(boolean submitToGallery, String title, String description, ImgurTopic topic) {
        ArrayList<Upload> uploads = mAdapter.retainItems();
        Intent uploadSerivce = UploadService2.createIntent(getApplicationContext(), uploads, submitToGallery, title, description, topic);
        startService(uploadSerivce);
        finish();
    }

    @Override
    public void onBackPressed() {
        Fragment f = getFragmentManager().findFragmentByTag(UploadInfoFragment.class.getSimpleName());

        if (f != null) {
            getFragmentManager()
                    .beginTransaction()
                    .remove(f)
                    .commit();

            getSupportActionBar().show();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Checks if we have cached topics to display for the info fragment
     */
    private void checkForTopics() {
        List<ImgurTopic> topics = app.getSql().getTopics();

        if (topics == null || topics.isEmpty()) {
            LogUtil.v(TAG, "No topics found, fetching");
            EventBus.getDefault().register(this);
            new ApiClient(Endpoints.TOPICS_DEFAULTS.getUrl(), ApiClient.HttpRequest.GET).doWork(ImgurBusEvent.EventType.TOPICS, null, null);
        } else {
            LogUtil.v(TAG, "Topics in database");
        }
    }

    public void onEventAsync(@NonNull ImgurBusEvent event) {
        if (event.eventType == ImgurBusEvent.EventType.TOPICS) {
            try {
                int statusCode = event.json.getInt(ApiClient.KEY_STATUS);

                if (statusCode == ApiClient.STATUS_OK) {
                    List<ImgurTopic> topics = new ArrayList<>();
                    JSONArray array = event.json.getJSONArray(ApiClient.KEY_DATA);

                    for (int i = 0; i < array.length(); i++) {
                        topics.add(new ImgurTopic(array.getJSONObject(i)));
                    }

                    app.getSql().addTopics(topics);
                } else {
                    // TODO Some error?
                }

            } catch (JSONException e) {
                LogUtil.e(TAG, "Error parsing JSON", e);
                // What to do on error?
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private ItemTouchHelper.SimpleCallback mSimpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            mAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
            Upload upload = mAdapter.removeItem(viewHolder.getAdapterPosition());
            if (mAdapter.isEmpty()) mMultiView.setViewState(MultiStateView.ViewState.EMPTY);

            SnackBar.cancelSnackBars(UploadActivity2.this);
            new SnackBarItem.Builder(UploadActivity2.this)
                    .setMessageResource(R.string.upload_photo_removed)
                    .setActionMessageResource(R.string.undo)
                    .setObject(upload)
                    .setSnackBarListener(new SnackBarListener() {
                        @Override
                        public void onSnackBarStarted(Object o) {

                        }

                        @Override
                        public void onSnackBarFinished(Object object, boolean actionPressed) {
                            if (actionPressed && object instanceof Upload) {
                                mMultiView.setViewState(MultiStateView.ViewState.CONTENT);
                                mAdapter.addItem((Upload) object);
                                supportInvalidateOptionsMenu();
                            }
                        }
                    })
                    .show();

            supportInvalidateOptionsMenu();
        }
    };
}
