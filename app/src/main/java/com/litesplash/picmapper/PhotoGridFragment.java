package com.litesplash.picmapper;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;


public class PhotoGridFragment extends Fragment implements PhotoGridAdapter.OnItemClickListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String MARKER_ARRAY_LIST = "markerArrayList";
    private static final String TOP_MARGIN = "topMargin";
    private static final String LOAD_PHOTO_TAG = "loadPhoto";
    private static final String LOG_TAG = "PhotoGridFragment";

    private Context context;
    private Listener listener;
    private ArrayList<PhotoItem> markerArrayList;

    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;

    private int topMargin;

    private boolean exiting = false;

    public static PhotoGridFragment newInstance(ArrayList<PhotoItem> markerArrayList, int topMargin) {
        PhotoGridFragment fragment = new PhotoGridFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(MARKER_ARRAY_LIST, markerArrayList);
        args.putInt(TOP_MARGIN, topMargin);
        fragment.setArguments(args);
        return fragment;
    }

    public PhotoGridFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            markerArrayList = getArguments().getParcelableArrayList(MARKER_ARRAY_LIST);
            topMargin = getArguments().getInt(TOP_MARGIN);
        }
        context = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.cluster_grid_fragment, container, false);

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) rootView.getLayoutParams();
        layoutParams.setMargins(0, topMargin, 0, 0);
        rootView.setLayoutParams(layoutParams);

        recyclerView = (AutoFitRecyclerView) rootView.findViewById(R.id.photo_recycler_view);

        adapter = new PhotoGridAdapter(markerArrayList, context, this);

        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                Picasso picasso = Picasso.with(context);

                //pause image loading when flinging thru list to prevent stuttering
                if (newState == RecyclerView.SCROLL_STATE_SETTLING)
                    picasso.pauseTag(LOAD_PHOTO_TAG);
                else
                    picasso.resumeTag(LOAD_PHOTO_TAG);

                super.onScrollStateChanged(recyclerView, newState);
            }
        });

        return rootView;
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {

        Animator anim;

        if (enter) {
            anim = AnimatorInflater.loadAnimator(getActivity(), R.animator.slide_up);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!exiting) {
                        Log.d(LOG_TAG, "onAnimationEnd called for slide up");
                        listener.onPhotoGridFragmentShown();
                    }
                }
            });
        } else {
            anim = AnimatorInflater.loadAnimator(getActivity(), R.animator.slide_down);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    Log.d(LOG_TAG, "onAnimationStart called for slide down");
                    listener.onPhotoGridFragmentHidden();
                    exiting = true;
                }
            });
        }
        return anim;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement PhotoGridFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onPhotoItemClick(View view, int position) {
        PhotoItem pm = markerArrayList.get(position);
        listener.onPhotoGridItemClick(pm);
    }

    public interface Listener {
        void onPhotoGridItemClick(PhotoItem photoItem);
        void onPhotoGridFragmentShown();
        void onPhotoGridFragmentHidden();
    }
}
