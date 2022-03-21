package com.emupapps.free_editor.ui.fragments;

import static android.app.Activity.RESULT_OK;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;
import static com.emupapps.free_editor.adapters.MergedVideosAdapter.LAST_ITEM;
import static com.emupapps.free_editor.adapters.MergedVideosAdapter.MERGED;
import static com.emupapps.free_editor.adapters.MergedVideosAdapter.MERGE_PROCESS;
import static com.emupapps.free_editor.adapters.MergedVideosAdapter.MERGING;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.FFprobe;
import com.arthenica.mobileffmpeg.MediaInformation;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.emupapps.free_editor.Data.MergeVideoModel;
import com.emupapps.free_editor.Data.TinyDB;
import com.emupapps.free_editor.R;
import com.emupapps.free_editor.adapters.MergedVideosAdapter;
import com.emupapps.free_editor.view_models.MainViewPagerSwipingViewModel;
import com.emupapps.free_editor.view_models.MergeViewModel;
import com.emupapps.free_editor.view_models.ToolbarViewModel;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MergeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MergeFragment extends Fragment {

    private static final String TAG = MergeFragment.class.getSimpleName();
    private static final int VIDEOS_LIST_FIRST_POSITION = 0;
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    static final int OPEN_MEDIA_PICKER = 1;
    TinyDB tinydb;
    String mylink = "https://www.google.com/";
    private MergeViewModel mMergeViewModel;
    ImageView mBanner;
    String saldo = "";

    private ToolbarViewModel mToolbarViewModel;
    private MainViewPagerSwipingViewModel mMainViewPagerSwipingViewModel;

    private ImageView mIcVideo2;
    private ImageView mIcVideo;
    private Button mOpenGallery;
    private TextView mPickUpMerge;
    private RecyclerView mRecyclerViewVideos;
    private VideoView mVideoView;
    private View mVideoViewBackground;
    private View mViewShadow;
    private AppCompatSeekBar mVideoSeekBar;
    private ImageView mIcVideoControl;
    private ImageView mVoiceControl;
    private TextView mVideoName;
    private boolean mVideoControlsVisible = false;
    private TextView mVideoTime;
    private boolean mBlockSeekBar = true;
    private boolean mVideoMuted = false;
    private MediaPlayer mMediaPlayer;
    private MergedVideosAdapter mMergedVideosAdapter;
    private List<MergeVideoModel> mListVideos;
    private List<MergeVideoModel> mListVideosForMergeProcess;
    private int mLastClickedVideo = -1;
    private boolean mPaused;
    private Handler mUpdateVideoTimeHandler = new Handler();
    private Handler mHideVideoControlsHandler = new Handler();
    private File mergedFile;
    private Toast mToast;
    int mergedVideoFrames = 0;
    private long mFFmpegMergeProcessId;

    private Runnable mUpdateVideoTimeRunnable = new Runnable() {
        @Override
        public void run() {
            long currentPosition = mVideoView.getCurrentPosition();
            mVideoSeekBar.setProgress((int) currentPosition);
            mVideoTime.setText(String.format(Locale.ENGLISH, "%s / %s",
                    getVideoTime((int) currentPosition / 1000),
                    getVideoTime(mVideoView.getDuration() / 1000)));
            mUpdateVideoTimeHandler.postDelayed(this, 10);
        }
    };

    private Runnable mHideVideoControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVideoView.isPlaying() && mVideoControlsVisible) {
                if (mVideoView.isPlaying() && mVideoControlsVisible) {
                    mVideoSeekBar.animate().alpha(0);
                    mBlockSeekBar = true;
                    mIcVideoControl.animate().alpha(0);
                    mViewShadow.animate().alpha(0);
                    mVideoTime.animate().alpha(0);
                    mVoiceControl.setClickable(false);
                    mVoiceControl.animate().alpha(0);
                    mVideoControlsVisible = false;
                }
            }
        }
    };

    ItemTouchHelper.SimpleCallback mSimpleCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START |
                    ItemTouchHelper.END, 0) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {

            if (viewHolder.getAdapterPosition() == mListVideos.size() - 1 ||
                    target.getAdapterPosition() == mListVideos.size() - 1)
                return true;

            if (mMergedVideosAdapter.getVideosList().get(0).getType() == MERGE_PROCESS)
                return true;

            int fromPosition = viewHolder.getAdapterPosition();
            int toPosition = target.getAdapterPosition();

            Collections.swap(mListVideos, fromPosition, toPosition);
            recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);

            for (MergeVideoModel video : mListVideos) {
                if (video.getVideoMode() == MergeVideoModel.Mode.PLAY) {
                    mLastClickedVideo = mListVideos.indexOf(video);
                }
            }

            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

        }
    };

    public MergeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MergeFragment.
     */
    public static MergeFragment newInstance(String param1, String param2) {
        MergeFragment fragment = new MergeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_merge, container, false);

        mMainViewPagerSwipingViewModel = ViewModelProviders.of(getActivity())
                .get(MainViewPagerSwipingViewModel.class);
        mBanner = view.findViewById(R.id.banner);

        mIcVideo2 = view.findViewById(R.id.icVideo2);
        mIcVideo = view.findViewById(R.id.icVideo);
        mOpenGallery = view.findViewById(R.id.openGallery);
        mPickUpMerge = view.findViewById(R.id.pickUpMerge);
        mRecyclerViewVideos = view.findViewById(R.id.recyclerViewVideos);
        mVideoView = view.findViewById(R.id.videoView);
        mVideoViewBackground = view.findViewById(R.id.videoViewBackground);
        mViewShadow = view.findViewById(R.id.shadow);
        mVideoSeekBar = view.findViewById(R.id.videoSeekBar);
        mIcVideoControl = view.findViewById(R.id.icVideoControl);
        mVoiceControl = view.findViewById(R.id.voiceControl);
        mVideoName = view.findViewById(R.id.videoName);
        mVideoTime = view.findViewById(R.id.videoTime);

        mListVideos = new ArrayList<>();
        mListVideosForMergeProcess = new ArrayList<>();
        mMergeViewModel = ViewModelProviders.of(getActivity()).get(MergeViewModel.class);
        mToolbarViewModel = ViewModelProviders.of(getActivity()).get(ToolbarViewModel.class);

        MobileAds.initialize(getActivity(), "ca-app-pub-6503532425142366~2924525320");

        AdView adView1 = view.findViewById(R.id.adView22);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView1.loadAd(adRequest);

        tinydb = new TinyDB(getActivity());

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(),
                RecyclerView.VERTICAL, false);
        mRecyclerViewVideos.setLayoutManager(layoutManager);
        mRecyclerViewVideos.setHasFixedSize(true);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(mSimpleCallback);
        itemTouchHelper.attachToRecyclerView(mRecyclerViewVideos);

        mBanner.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String url = mylink;
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        mOpenGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setType("video/*");
                startActivityForResult(intent, OPEN_MEDIA_PICKER);
            }
        });

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (mPaused)
                    return;

                mListVideos.get(mLastClickedVideo).setVideoMode(MergeVideoModel.Mode.PLAY);
                mMergedVideosAdapter.notifyItemChanged(mLastClickedVideo);
                int videoDuration = mVideoView.getDuration() / 1000;
                mUpdateVideoTimeHandler.postDelayed(mUpdateVideoTimeRunnable, 10);
                mMediaPlayer = mp;
                mVoiceControl.setImageResource(R.drawable.ic_speaker);
                mVideoMuted = false;
                mIcVideoControl.setImageResource(R.drawable.ic_pause);
                mVideoView.start();
                mBlockSeekBar = true;
                mVideoSeekBar.setAlpha(0);
                mViewShadow.setAlpha(0);
                mIcVideoControl.setAlpha((float) 0);
                mVideoTime.setAlpha(0);
                mVoiceControl.setClickable(false);
                mVoiceControl.setAlpha((float) 0);
                mVideoControlsVisible = false;
                mVideoSeekBar.setProgress(0);
                mVideoSeekBar.setMax(mVideoView.getDuration());
                mVideoTime.setText(String.format(Locale.ENGLISH, "%s / %s",
                        getVideoTime(0),
                        getVideoTime(videoDuration)));

                showVideoControls();
                controlVideo();
                controlVideoSeekbar();
                controlVideoVoice();

                mVideoSeekBar.setProgress(0);
                mVideoSeekBar.setMax(mVideoView.getDuration());
                mVideoTime.setText(String.format(Locale.ENGLISH, "%s / %s",
                        getVideoTime(0),
                        getVideoTime(videoDuration)));
            }
        });

        mVideoView.setOnCompletionListener(mp -> {
            mUpdateVideoTimeHandler.removeCallbacks(mUpdateVideoTimeRunnable);
            mBlockSeekBar = false;
            mVideoSeekBar.animate().alpha(1);
            mViewShadow.animate().alpha(1);
            mIcVideoControl.animate().alpha(1);
            mVoiceControl.setClickable(true);
            mVoiceControl.animate().alpha(1);
            mVideoTime.animate().alpha(1);
            mVideoControlsVisible = true;
            mIcVideoControl.setImageResource(R.drawable.ic_play);
        });

        return view;
    }

    @Override
    public void onResume() {
        if (mLastClickedVideo == -1)
            mMainViewPagerSwipingViewModel.setMainViewPagerSwiping(true);
        else
            mMainViewPagerSwipingViewModel.setMainViewPagerSwiping(false);
        super.onResume();
    }

    @Override
    public void onPause() {
        mPaused = true;
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
            mBlockSeekBar = false;
            mVideoSeekBar.animate().alpha(1);
            mViewShadow.animate().alpha(1);
            mIcVideoControl.animate().alpha(1);
            mVoiceControl.setClickable(true);
            mVoiceControl.animate().alpha(1);
            mVideoTime.animate().alpha(1);
            mVideoControlsVisible = true;
            mIcVideoControl.setImageResource(R.drawable.ic_play);
            mListVideos.get(mLastClickedVideo).setVideoMode(MergeVideoModel.Mode.PAUSE);
            mMergedVideosAdapter.notifyItemChanged(mLastClickedVideo);
            mUpdateVideoTimeHandler.removeCallbacks(mUpdateVideoTimeRunnable);
            mHideVideoControlsHandler.removeCallbacks(mHideVideoControlsRunnable);
        }
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_MEDIA_PICKER) {
            if (resultCode == RESULT_OK) {
                mMainViewPagerSwipingViewModel.setMainViewPagerSwiping(false);
                showPickupVideos(false);
                showVideoView(true);
                if (!mListVideos.isEmpty()) {
                    mListVideos.remove(mListVideos.size() - 1);
                }
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item videoItem = clipData.getItemAt(i);
                        Uri videoURI = videoItem.getUri();
                        String filePath = getPath(getActivity(), videoURI);
                        File file = new File(filePath);
                        mListVideos.add(new MergeVideoModel(file, file.getName(),
                                getVideoDuration(file), MergeVideoModel.Mode.PAUSE,
                                MERGING));
                    }
                } else {
                    Uri videoURI = data.getData();
                    String filePath = getPath(getActivity(), videoURI);
                    File file = new File(filePath);
                    mListVideos.add(new MergeVideoModel(file, file.getName(),
                            getVideoDuration(file), MergeVideoModel.Mode.PAUSE,
                            MERGING));
                }

                mListVideos.add(new MergeVideoModel(null, "",
                        "", null,
                        LAST_ITEM));

                mMergedVideosAdapter = new MergedVideosAdapter(getActivity(), mListVideos,
                        new MergedVideosAdapter.VideoReorderClickListener() {
                            @Override
                            public void onReorderClickListener() {
                                if (mToast != null)
                                    mToast.cancel();

                                mToast = Toast.makeText(getActivity(),
                                        R.string.hold_to_reorder, Toast.LENGTH_SHORT);

                                mToast.show();
                            }
                        },
                        new MergedVideosAdapter.VideoPlayClickListener() {
                            @Override
                            public void onPlayClickListener(int videoClicked) {
                                mPaused = false;
                                mListVideos.get(mLastClickedVideo).
                                        setVideoMode(MergeVideoModel.Mode.PAUSE);
                                mMergedVideosAdapter.notifyItemChanged(mLastClickedVideo);
                                mLastClickedVideo = videoClicked;
                                mVideoView.setVideoPath(mListVideos.get(videoClicked).
                                        getVideoFile().getAbsolutePath());
                                mVideoName.setText(mListVideos.get(videoClicked).getVideoName());
                            }
                        }, new MergedVideosAdapter.VideoShareClickListener() {
                    @Override
                    public void onShareClickListener(int videoClicked) {
                        shareVideo(mListVideos.get(videoClicked).getVideoFile()
                                .getAbsolutePath());
                    }
                }, new MergedVideosAdapter.VideoRemoveClickListener() {
                    @Override
                    public void onVideoRemoveClickListener(int videoClicked) {
                        if (mListVideos.get(videoClicked).getVideoMode() ==
                                MergeVideoModel.Mode.PLAY) {
                            mListVideos.remove(videoClicked);
                            if (mListVideos.size() == 1) {
                                mMergedVideosAdapter.clearVideosList();
                                mMainViewPagerSwipingViewModel.setMainViewPagerSwiping(true);
                                mLastClickedVideo = -1;
                                FFmpeg.cancel(mFFmpegMergeProcessId);
                                Config.resetStatistics();
                                showPickupVideos(true);
                                showVideoView(false);
                                return;
                            }
                            mMergedVideosAdapter.swapVideosList(mListVideos);
                            mLastClickedVideo = VIDEOS_LIST_FIRST_POSITION;
                            mVideoView.setVideoPath(mListVideos.get(VIDEOS_LIST_FIRST_POSITION).
                                    getVideoFile().getAbsolutePath());
                            mVideoName.setText(mListVideos.get(VIDEOS_LIST_FIRST_POSITION).
                                    getVideoName());
                        } else {
                            mListVideos.remove(videoClicked);
                            if (mListVideos.size() == 1) {
                                mMergedVideosAdapter.clearVideosList();
                                mMainViewPagerSwipingViewModel.setMainViewPagerSwiping(true);
                                mLastClickedVideo = -1;
                                FFmpeg.cancel(mFFmpegMergeProcessId);
                                showPickupVideos(true);
                                showVideoView(false);
                                return;
                            }
                            mMergedVideosAdapter.swapVideosList(mListVideos);
                            for (MergeVideoModel video : mListVideos) {
                                if (video.getVideoMode() == MergeVideoModel.Mode.PLAY) {
                                    mLastClickedVideo = mListVideos.indexOf(video);
                                }
                            }
                        }
                    }
                },
                        new MergedVideosAdapter.ButtonMergeClickListener() {
                            @Override
                            public void onButtonMergeClickListener() {
                                if (mListVideos.get(VIDEOS_LIST_FIRST_POSITION).getType() ==
                                        MERGE_PROCESS) {
                                    if (mToast != null)
                                        mToast.cancel();

                                    mToast = Toast.makeText(getActivity(),
                                            R.string.merge_process_in_progress, Toast.LENGTH_SHORT);

                                    mToast.show();
                                } else {
                                    //changeVideoResolution();
                                    try {
                                       mergeVideos();
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }, new MergedVideosAdapter.ButtonGalleryClickListener() {
                    @Override
                    public void onButtonGalleryClickListener() {
                        if (mListVideos.get(VIDEOS_LIST_FIRST_POSITION).getType() ==
                                MERGE_PROCESS) {
                            if (mToast != null)
                                mToast.cancel();

                            mToast = Toast.makeText(getActivity(),
                                    R.string.merge_process_in_progress, Toast.LENGTH_SHORT);

                            mToast.show();
                        } else {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                            intent.setType("video/*");
                            startActivityForResult(intent, OPEN_MEDIA_PICKER);
                        }
                    }
                }, new MergedVideosAdapter.ButtonDeleteAllClickListener() {
                    @Override
                    public void onButtonDeleteAllClickListener() {
                        mMergedVideosAdapter.clearVideosList();
                        mMainViewPagerSwipingViewModel.setMainViewPagerSwiping(true);
                        mLastClickedVideo = -1;
                        FFmpeg.cancel(mFFmpegMergeProcessId);
                        Config.resetStatistics();
                        showPickupVideos(true);
                        showVideoView(false);
                    }
                });
                mLastClickedVideo = 0;
                mRecyclerViewVideos.setAdapter(mMergedVideosAdapter);
                mRecyclerViewVideos.setVisibility(View.VISIBLE);
                mPaused = false;
                mVideoView.setVideoPath(mListVideos.get(0).getVideoFile().getAbsolutePath());
                mVideoName.setText(mListVideos.get(0).getVideoName());
            }
        }
    }

    private void showPickupVideos(boolean visible) {
        if (visible) {
            mIcVideo2.setVisibility(View.VISIBLE);
            mIcVideo.setVisibility(View.VISIBLE);
            mOpenGallery.setVisibility(View.VISIBLE);
            mPickUpMerge.setVisibility(View.VISIBLE);
        } else {
            mIcVideo2.setVisibility(View.INVISIBLE);
            mIcVideo.setVisibility(View.INVISIBLE);
            mOpenGallery.setVisibility(View.INVISIBLE);
            mPickUpMerge.setVisibility(View.INVISIBLE);
        }
    }

    private void showVideoView(boolean visible) {
        if (visible) {
            mVideoView.setVisibility(View.VISIBLE);
            mVideoViewBackground.setVisibility(View.VISIBLE);
            mVideoSeekBar.setVisibility(View.VISIBLE);
            mViewShadow.setVisibility(View.VISIBLE);
            mIcVideoControl.setVisibility(View.VISIBLE);
            mVoiceControl.setVisibility(View.VISIBLE);
            mVideoTime.setVisibility(View.VISIBLE);
            mVideoName.setVisibility(View.VISIBLE);
            mRecyclerViewVideos.setVisibility(View.VISIBLE);
        } else {
            mVideoView.setVisibility(View.INVISIBLE);
            mVideoViewBackground.setVisibility(View.INVISIBLE);
            mVideoSeekBar.setVisibility(View.INVISIBLE);
            mViewShadow.setVisibility(View.INVISIBLE);
            mIcVideoControl.setVisibility(View.INVISIBLE);
            mVoiceControl.setVisibility(View.INVISIBLE);
            mVideoTime.setVisibility(View.INVISIBLE);
            mVideoName.setVisibility(View.INVISIBLE);
            mRecyclerViewVideos.setVisibility(View.INVISIBLE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showVideoControls() {
        mVideoView.setOnTouchListener((v, event) -> {
            if (mVideoControlsVisible) {
                mVideoSeekBar.animate().alpha(0);
                mBlockSeekBar = true;
                mIcVideoControl.animate().alpha(0);
                mVideoTime.animate().alpha(0);
                mViewShadow.animate().alpha(0);
                mVoiceControl.setClickable(false);
                mVoiceControl.animate().alpha(0);
                mVideoControlsVisible = false;
            } else {
                mBlockSeekBar = false;
                mVideoSeekBar.animate().alpha(1);
                mIcVideoControl.animate().alpha(1);
                mVideoTime.animate().alpha(1);
                mViewShadow.animate().alpha(1);
                mVoiceControl.setClickable(true);
                mVoiceControl.animate().alpha(1);
                mVideoControlsVisible = true;
                mHideVideoControlsHandler.postDelayed(mHideVideoControlsRunnable,
                        3000);
            }
            return false;
        });
    }

    private void controlVideo() {
        mIcVideoControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIcVideoControl.getAlpha() == 0) {
                    mBlockSeekBar = false;
                    mVideoSeekBar.animate().alpha(1);
                    mIcVideoControl.animate().alpha(1);
                    mVoiceControl.setClickable(true);
                    mVoiceControl.animate().alpha(1);
                    mVideoTime.animate().alpha(1);
                    mViewShadow.animate().alpha(1);
                    mVideoControlsVisible = true;
                    mHideVideoControlsHandler.postDelayed(mHideVideoControlsRunnable,
                            3000);
                    return;
                }
                if (mVideoView.isPlaying()) {
                    mIcVideoControl.setImageResource(R.drawable.ic_play);
                    mVideoView.pause();
                    mUpdateVideoTimeHandler.removeCallbacks(mUpdateVideoTimeRunnable);
                } else {
                    mIcVideoControl.setImageResource(R.drawable.ic_pause);
                    mVideoView.start();
                    mUpdateVideoTimeHandler.postDelayed(mUpdateVideoTimeRunnable, 10);
                    mBlockSeekBar = true;
                    mVideoSeekBar.setAlpha(0);
                    mIcVideoControl.setAlpha((float) 0);
                    mViewShadow.setAlpha(0);
                    mVideoTime.setAlpha(0);
                    mVoiceControl.setClickable(false);
                    mVoiceControl.setAlpha((float) 0);
                    mVideoControlsVisible = false;
                    mListVideos.get(mLastClickedVideo).setVideoMode(MergeVideoModel.Mode.PLAY);
                    mMergedVideosAdapter.notifyItemChanged(mLastClickedVideo);
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void controlVideoSeekbar() {
        mVideoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mVideoView.seekTo(progress);
                    mVideoTime.setText(String.format(Locale.ENGLISH, "%s / %s",
                            getVideoTime(progress / 1000),
                            getVideoTime(mVideoView.getDuration() / 1000)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mVideoView.pause();
                mHideVideoControlsHandler.removeCallbacks(mHideVideoControlsRunnable);
                mUpdateVideoTimeHandler.removeCallbacks(mUpdateVideoTimeRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mUpdateVideoTimeHandler.postDelayed(mUpdateVideoTimeRunnable, 10);
                mVideoView.start();
                mHideVideoControlsHandler.postDelayed(mHideVideoControlsRunnable,
                        3000);
                mIcVideoControl.setImageResource(R.drawable.ic_pause);
                mListVideos.get(mLastClickedVideo).setVideoMode(MergeVideoModel.Mode.PLAY);
                mMergedVideosAdapter.notifyItemChanged(mLastClickedVideo);
            }
        });

        mVideoSeekBar.setOnTouchListener((v, event) -> mBlockSeekBar);
    }

    private void controlVideoVoice() {
        mVoiceControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoMuted) {
                    mMediaPlayer.setVolume(1.0f, 1.0f);
                    mVoiceControl.setImageResource(R.drawable.ic_speaker);
                    mVideoMuted = false;
                } else {
                    mMediaPlayer.setVolume(0, 0);
                    mVoiceControl.setImageResource(R.drawable.ic_silent);
                    mVideoMuted = true;
                }
            }
        });
    }

    private int videoAvgFrameRate(MediaInformation mediaInformation) throws JSONException{
        String  avgFrameRateString = mediaInformation.getAllProperties()
                .getJSONArray("streams")
                .getJSONObject(0).getString("avg_frame_rate");
        String[] avgFrameRateArray = avgFrameRateString.split("/");
        return Integer.parseInt(avgFrameRateArray[0]) / Integer.parseInt(avgFrameRateArray[1]);
    }

    private void mergeTest() throws JSONException{
        File storageFile = new File(Environment.getExternalStorageDirectory(),
                "FreeCut/merge/");
        storageFile.mkdirs();

        String mergedFileName;

        int j = 1;

        do {
            mergedFileName = String.format(Locale.ENGLISH, "merge-%02d", j);
            mergedFile = new File(storageFile, mergedFileName + ".mp4");
            j++;
        } while (mergedFile.isFile());

        final String[] command = {
                "-i", mListVideos.get(0).getVideoFile().getAbsolutePath(),
                "-c:a", "aac", "-vcodec", "libx264", "-s",
                "1280" + "X" + "720",
                "-vf",
                "scale=1280:960:force_original_aspect_ratio=decrease,pad=1280:960:-1:-1:color=black",
                "-r","25",
                mergedFile.getAbsolutePath(),
        };

        final String[] command2 = {
                "-i", mListVideos.get(0).getVideoFile().getAbsolutePath(),
                "-vf",
                "scale=1280:960:force_original_aspect_ratio=decrease,pad=1280:960:-1:-1:color=black",
                "-r","25",
                mergedFile.getAbsolutePath(),
        };
        //540x960
        //640x360
        //1920X1080
        //1280X720
//                final String[] convert = {
//                "-i", mListVideos.get(0).getVideoFile().getAbsolutePath(),
//                "-c:a", "aac", "-vcodec", "libx264", "-s",
//                "1280" + "X" + "720",
//                "-r", "25", "-strict", "experimental",
//                        mergedFile.getAbsolutePath(),
//        };
        final String[] convert = {
                "-i", mListVideos.get(0).getVideoFile().getAbsolutePath(),
                "-c:a", "aac", "-vcodec", "libx264", "-s",
                "1280" + "X" + "720",
                mergedFile.getAbsolutePath(),
        };

        Log.d(TAG, "Command Start");
        FFmpeg.executeAsync(command, new ExecuteCallback() {
            @Override
            public void apply(long executionId, int returnCode) {
                if (returnCode == RETURN_CODE_SUCCESS) {
                    Log.d(TAG, "Command Success");
                    mMergedVideosAdapter.removeFromList(VIDEOS_LIST_FIRST_POSITION,
                            false);
                    mMergedVideosAdapter.addToList(VIDEOS_LIST_FIRST_POSITION,
                            new MergeVideoModel(mergedFile, mergedFile.getName(),
                                    getVideoDuration(mergedFile), MergeVideoModel.Mode.PAUSE,
                                    MERGED));
                    FFmpeg.cancel(mFFmpegMergeProcessId);
                } else {
                    Log.d(TAG, "Command Failed");
                    FFmpeg.cancel(mFFmpegMergeProcessId);
                }
            }
        });
    }

    private void mergeVideos() throws JSONException {
        mListVideosForMergeProcess.clear();
        mListVideosForMergeProcess.addAll(mListVideos);

        File storageFile = new File(Environment.getExternalStorageDirectory(),
                "FreeCut/merge/");
        storageFile.mkdirs();

        String mergedFileName;

        int j = 1;

        do {
            mergedFileName = String.format(Locale.ENGLISH, "merge-%02d", j);
            mergedFile = new File(storageFile, mergedFileName + ".mp4");
            j++;
        } while (mergedFile.isFile());

        MediaInformation infoOfFirstVideo =
                FFprobe.getMediaInformation(mListVideos.get(0).getVideoFile().getAbsolutePath());
        int heightOfFirstVideo = Integer.parseInt(infoOfFirstVideo.getAllProperties()
                .getJSONArray("streams")
                .getJSONObject(0).getString("height"));
        int widthOfFirstVideo = Integer.parseInt(infoOfFirstVideo.getAllProperties()
                .getJSONArray("streams")
                .getJSONObject(0).getString("width"));
        int fpsOfFirstVideo = videoAvgFrameRate(infoOfFirstVideo);

        //boolean differentVideoQualities = false;
        MediaInformation videoInfo;
        int videoHeight;
        int videoWidth;
        int videoFps;
        int totalFrames = 0;
//        for (int i = 0; i < mListVideos.size() - 1; i++) {
//            videoInfo = FFprobe.getMediaInformation(mListVideos
//                    .get(i).getVideoFile().getAbsolutePath());
//            videoHeight = Integer.parseInt(videoInfo.getAllProperties()
//                    .getJSONArray("streams")
//                    .getJSONObject(0).getString("height"));
//            videoWidth = Integer.parseInt(videoInfo.getAllProperties()
//                    .getJSONArray("streams")
//                    .getJSONObject(0).getString("width"));
//            videoFps = videoAvgFrameRate(videoInfo);
//            totalFrames += getVideoSeconds(mListVideos.get(i).getVideoFile()) * videoFps;
//            if (videoHeight != heightOfFirstVideo || videoFps != fpsOfFirstVideo ||
//            videoWidth != widthOfFirstVideo) {
//                Log.d(TAG, "differentVideoQualities");
//                differentVideoQualities = true;
//                break;
//            }
//        }

        int maximumVideoHeight = heightOfFirstVideo;
        int maximumVideoWidth = widthOfFirstVideo;
        int minimumFps = fpsOfFirstVideo;

        //if (differentVideoQualities) {
            for (int i = 0; i < mListVideos.size() - 1; i++) {
                videoInfo = FFprobe.getMediaInformation(mListVideos
                        .get(i).getVideoFile().getAbsolutePath());
                videoHeight = Integer.parseInt(videoInfo.getAllProperties()
                        .getJSONArray("streams")
                        .getJSONObject(0).getString("height"));
                videoWidth = Integer.parseInt(videoInfo.getAllProperties()
                        .getJSONArray("streams")
                        .getJSONObject(0).getString("width"));
                videoFps = videoAvgFrameRate(videoInfo);
                if (videoHeight > maximumVideoHeight) {
                    maximumVideoHeight = videoHeight;
                }
                if (videoWidth > maximumVideoWidth) {
                    maximumVideoWidth = videoWidth;
                }
                if (videoFps < minimumFps) {
                    minimumFps = videoFps;
                }
                totalFrames = 0;
            }

            for (int i = 0; i < mListVideos.size() - 1; i++) {
                totalFrames += getVideoSeconds(mListVideos.get(i).getVideoFile()) * minimumFps;
            }

            File convertedStorageFile = new File(Environment.getExternalStorageDirectory(),
                    "FreeCut/merge/converted");
            if (convertedStorageFile.exists()){
                deleteFiles(convertedStorageFile);
            }
            enableAnalytics(totalFrames * 2);
            convertedStorageFile.mkdirs();
            if(maximumVideoHeight > 960){
                maximumVideoHeight = 960;
            }
            convertVideo(0, maximumVideoHeight, maximumVideoWidth, minimumFps,
                    convertedStorageFile);
            //mergingVideos(null);
//        }
//        else {
//            enableAnalytics(totalFrames);
//            mergingVideos(null);
//        }
    }

    private int count = 0;
    private int total = 0;

    private void enableAnalytics(int allFrames){
        Log.d(TAG, "allFrames : " + allFrames);
        total = 0;
        mMergedVideosAdapter.addToList(VIDEOS_LIST_FIRST_POSITION,
                new MergeVideoModel(mergedFile, mergedFile.getName(),
                        "", MergeVideoModel.Mode.PAUSE,
                        MERGE_PROCESS));

        for (MergeVideoModel video : mListVideos) {
            if (video.getVideoMode() == MergeVideoModel.Mode.PLAY) {
                mLastClickedVideo = mListVideos.indexOf(video);
            }
        }

        Config.enableStatisticsCallback(new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                if(statistics.getVideoFrameNumber() - count < 0){
                    count = 0;
                } else{
                    total += (statistics.getVideoFrameNumber() - count);
                }
                count += (statistics.getVideoFrameNumber() - count);

                double progress =
                        ((double) total / allFrames) * 100;

                if (progress <= 100 && mListVideos.get(VIDEOS_LIST_FIRST_POSITION).
                        getType() == MERGE_PROCESS) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mRecyclerViewVideos.
                                    findViewHolderForAdapterPosition(VIDEOS_LIST_FIRST_POSITION) != null)
                                ((MergedVideosAdapter.MergeProcessViewHolder)
                                        mRecyclerViewVideos.
                                                findViewHolderForAdapterPosition(VIDEOS_LIST_FIRST_POSITION)).
                                        updateProgress((int) progress);
                        }
                    });
                }
            }
        });
    }

    private void mergingVideos(File convertedDirectory) {
        ArrayList<String> listMergeCommands = new ArrayList<>();
        String filter = "";
        for (int i = 0; i < mListVideosForMergeProcess.size() - 1; i++) {
            filter += String.format(Locale.ENGLISH, "[%d:v] [%d:a] ", i, i);
            listMergeCommands.add("-i");
            listMergeCommands.add(mListVideosForMergeProcess
                    .get(i).getVideoFile().getAbsolutePath());
            MediaInformation info = FFprobe.getMediaInformation(
                    mListVideosForMergeProcess.get(i).getVideoFile().getAbsolutePath());

            try {
                JSONArray array = info.getAllProperties().getJSONArray("streams");
                JSONObject object = array.getJSONObject(0);
                int videoFrames = Integer.parseInt(object.getString("nb_frames"));
                mergedVideoFrames += videoFrames;
                int height = Integer.parseInt(info.getAllProperties()
                        .getJSONArray("streams")
                        .getJSONObject(0).getString("height"));
                int width = Integer.parseInt(info.getAllProperties()
                        .getJSONArray("streams")
                        .getJSONObject(0).getString("width"));
                int fps = videoAvgFrameRate(info);
                Log.d(TAG, width + "/" + height + "/" + fps);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        filter += String.format(Locale.ENGLISH, "concat=n=%d:v=1:a=1 [v] [a]",
                mListVideosForMergeProcess.size() - 1);
        listMergeCommands.add("-filter_complex");
        listMergeCommands.add(filter);
        listMergeCommands.add("-map");
        listMergeCommands.add("[v]");
        listMergeCommands.add("-map");
        listMergeCommands.add("[a]");
        listMergeCommands.add(mergedFile.getAbsolutePath());

        String[] mergeCommand = listMergeCommands.toArray(new String[listMergeCommands.size()]);

        for (MergeVideoModel video : mListVideos) {
            if (video.getVideoMode() == MergeVideoModel.Mode.PLAY) {
                mLastClickedVideo = mListVideos.indexOf(video);
            }
        }

        Log.d(TAG, "Merge Start");
        mFFmpegMergeProcessId = FFmpeg.executeAsync(mergeCommand, new ExecuteCallback() {
            @Override
            public void apply(long executionId, int returnCode) {
                if (returnCode == RETURN_CODE_SUCCESS) {
                    Log.d(TAG, "Merge Success");
                    mMergedVideosAdapter.removeFromList(VIDEOS_LIST_FIRST_POSITION,
                            false);
                    mMergedVideosAdapter.addToList(VIDEOS_LIST_FIRST_POSITION,
                            new MergeVideoModel(mergedFile, mergedFile.getName(),
                                    getVideoDuration(mergedFile), MergeVideoModel.Mode.PAUSE,
                                    MERGED));
                    if (convertedDirectory != null){
                        deleteFiles(convertedDirectory);
                    }
                    FFmpeg.cancel(mFFmpegMergeProcessId);
                } else {
                    Log.d(TAG, "Merge Failed");
                    FFmpeg.cancel(mFFmpegMergeProcessId);
                }
            }
        });
    }

    private void convertVideo(int counter, int maxHeight, int maxWidth,
                              int minFps, File parent) {
        if (counter == mListVideosForMergeProcess.size() - 1) {
            mergingVideos(parent);
            return;
        }
        File video = mListVideosForMergeProcess.get(counter).getVideoFile();
        final int nextVideo = counter + 1;

        File convertedFile = new File(parent,
                video.getName() + "-converted" + counter + ".mp4");

        final String[] command = {
                "-i", video.getAbsolutePath(),
                "-c:a", "aac", "-vcodec", "libx264", "-s",
                "1280" + "X" + "720",
                "-vf",
                "scale=1280:"+maxHeight+":force_original_aspect_ratio=decrease," +
                        "pad=1280:"+maxHeight+":-1:-1:color=black",
                "-r",String.valueOf(minFps),
                convertedFile.getAbsolutePath(),
        };

        FFmpeg.executeAsync(command, new ExecuteCallback() {
            @Override
            public void apply(long executionId, int returnCode) {
                Log.d(TAG, "convert start");
                if (returnCode == RETURN_CODE_SUCCESS) {
                    Log.d(TAG, "convert success");
                    String fileName = mListVideosForMergeProcess.get(counter).getVideoName();
                    mListVideosForMergeProcess.remove(counter);
                    mListVideosForMergeProcess.add(counter, new MergeVideoModel(convertedFile,
                            fileName,
                            getVideoDuration(convertedFile), MergeVideoModel.Mode.PAUSE,
                            MERGING));
                    convertVideo(nextVideo, maxHeight, maxWidth, minFps, parent);
                } else {
                    Log.d(TAG, "convert failed");
                }
            }
        });
    }

    private boolean deleteFiles(File directory){
        File[] includedFiles = directory.listFiles();
        if(includedFiles != null){
            for(int i = 0 ; i < includedFiles.length ; i++){
                includedFiles[i].delete();
            }
            directory.delete();
            return true;
        }
        directory.delete();
        return true;
    }

    private int getVideoSeconds(File videoFile) {
        if (videoFile == null)
            return 0;
        String videoPath = videoFile.getAbsolutePath();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String time =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        return Integer.parseInt(time) / 1000;
    }

    private String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {

            // ExternalStorageProvider

            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                } else {
                    // Below logic is how External Storage provider build URI for documents
                    // Based on http://stackoverflow.com/questions/28605278/android-5-sd-card-label and https://gist.github.com/prasad321/9852037
                    StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

                    try {
                        Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
                        Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
                        Method getUuid = storageVolumeClazz.getMethod("getUuid");
                        Method getState = storageVolumeClazz.getMethod("getState");
                        Method getPath = storageVolumeClazz.getMethod("getPath");
                        Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
                        Method isEmulated = storageVolumeClazz.getMethod("isEmulated");

                        Object result = getVolumeList.invoke(mStorageManager);

                        final int length = Array.getLength(result);
                        for (int i = 0; i < length; i++) {
                            Object storageVolumeElement = Array.get(result, i);
                            //String uuid = (String) getUuid.invoke(storageVolumeElement);

                            final boolean mounted = Environment.MEDIA_MOUNTED.equals(getState.invoke(storageVolumeElement))
                                    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(getState.invoke(storageVolumeElement));

                            //if the media is not mounted, we need not get the volume details
                            if (!mounted) continue;

                            //Primary storage is already handled.
                            if ((Boolean) isPrimary.invoke(storageVolumeElement) && (Boolean) isEmulated.invoke(storageVolumeElement))
                                continue;

                            String uuid = (String) getUuid.invoke(storageVolumeElement);

                            if (uuid != null && uuid.equals(type)) {
                                String res = getPath.invoke(storageVolumeElement) + "/" + split[1];
                                return res;
                            }
                        }
                    } catch (Exception ex) {
                    }
                }
            }


            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                if (!TextUtils.isEmpty(id)) {
                    if (id.startsWith("raw:")) {
                        return id.replaceFirst("raw:", "");
                    }
                }
                String[] contentUriPrefixesToTry = new String[]{
                        "content://downloads/public_downloads",
                        "content://downloads/my_downloads",
                        "content://downloads/all_downloads"
                };

                for (String contentUriPrefix : contentUriPrefixesToTry) {
                    Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.valueOf(id));
                    try {
                        String path = getDataColumn(context, contentUri, null, null);
                        if (path != null) {
                            return path;
                        }
                    } catch (Exception e) {
                    }
                }

                return null;
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

//            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /**
     * Get the value of the data column for this Uri.
     */
    private String getDataColumn(Context context, Uri uri, String selection,
                                 String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private String getVideoDuration(File videoFile) {
        if (videoFile == null)
            return "";
        String videoPath = videoFile.getAbsolutePath();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String time =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        int seconds = Integer.parseInt(time) / 1000;
        return getVideoTime(seconds);
    }

    private String getVideoTime(int seconds) {
        long second = seconds % 60;
        long minute = (seconds / 60) % 60;
        long hour = (seconds / (60 * 60)) % 24;

        if (hour > 0)
            return String.format(Locale.ENGLISH, "%d:%d:%02d", hour, minute, second);
        else
            return String.format(Locale.ENGLISH, "%d:%02d", minute, second);
    }

    private boolean isBetween(int value, int min, int max) {
        if (value > min && value < max) {
            return true;
        } else {
            return false;
        }
    }

    private void shareVideo(String videoPath) {
        Uri uri = FileProvider.getUriForFile(getActivity(),
                getActivity().getApplicationContext()
                        .getPackageName() + ".provider", new File(videoPath));

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("video/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        getActivity().startActivity(Intent.createChooser(shareIntent, ""));
    }
}