package com.android.yt_legasy_a21;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class VideoListAdapter extends ArrayAdapter<String> {

    private Context mContext;
    private ArrayList<String> mTitles;
    private ArrayList<String> mThumbs;

    public VideoListAdapter(Context context, ArrayList<String> titles, ArrayList<String> thumbnailUrls) {
        super(context, 0, titles);
        this.mContext = context;
        this.mTitles = titles;
        this.mThumbs = thumbnailUrls;
    }

    static class ViewHolder {
        ImageView thumbnail;
        TextView title;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.list_item_video, parent, false);
            holder = new ViewHolder();
            holder.thumbnail = convertView.findViewById(R.id.thumbnail);
            holder.title = convertView.findViewById(R.id.videoTitle);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String title = mTitles.get(position);
        String thumbUrl = (mThumbs != null && position < mThumbs.size()) ? mThumbs.get(position) : "";

        holder.title.setText(title);

        if (thumbUrl != null && thumbUrl.length() > 0) {
            try {
                Picasso.with(mContext)
                        .load(thumbUrl)
                        .placeholder(android.R.drawable.ic_menu_report_image)
                        .error(android.R.drawable.ic_menu_report_image)
                        .fit()
                        .centerCrop()
                        .into(holder.thumbnail);
            } catch (Exception e) {
                holder.thumbnail.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        } else {
            holder.thumbnail.setImageResource(android.R.drawable.ic_menu_report_image);
        }




        ImageButton menuButton = convertView.findViewById(R.id.menuButton);

// 三点ボタン押下 → コンテキストメニュー起動
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 長押しと同じメニューを表示
                v.showContextMenu();
            }
        });
        return convertView;
    }
}

