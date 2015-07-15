package com.litesplash.photomap;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;


public class PhotoListFragment extends Fragment implements PhotoAdapter.OnItemClickListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "markerArrayList";
    private static final String LOAD_PHOTO_TAG = "loadPhoto";

    private Context context;
    private PhotoListItemClickListener listener;
    private ArrayList<PhotoMarker> markerArrayList;

    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private RecyclerView.Adapter adapter;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment PhotoListFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static PhotoListFragment newInstance(String param1, String param2) {
        PhotoListFragment fragment = new PhotoListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        fragment.setArguments(args);
        return fragment;
    }

    public PhotoListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            markerArrayList = getArguments().getParcelableArrayList(ARG_PARAM1);
        }
        context = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.cluster_list_fragment, container, false);
        recyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);

        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new PhotoAdapter(markerArrayList, context, this);

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

        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (PhotoListItemClickListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement PhotoListItemClickListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onPhotoItemClick(View view, int position) {
        PhotoMarker pm = markerArrayList.get(position);
        listener.onPhotoListItemClick(pm);
    }

    public interface PhotoListItemClickListener {
        void onPhotoListItemClick(PhotoMarker photoMarker);
    }
}
