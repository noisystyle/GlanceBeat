package it.noxia.ambientplayer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A recycled native Material queue over the player's frost; only visible pages observe updates. */
@android.annotation.SuppressLint({"ViewConstructor","ClickableViewAccessibility"}) // Programmatic page; clickable native child cards/buttons expose their actions.
final class QueuePanel extends FrameLayout {
    private final Runnable interact,close,changed;
    private final int accent;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinearLayout column;
    private final TextView count,empty,message;
    private final FrostedQueueList list;
    private final HeaderFrost headerFrost;
    private final QueueArtwork covers;
    private final Rows adapter=new Rows();
    private MusicQueue.Snapshot snapshot=MusicQueue.Snapshot.EMPTY;
    private QueueSelection.Item pending;
    private MediaSession.Token pendingSession;
    private float scale=1,downX,downY;
    private int viewportWidth,viewportHeight;
    private android.graphics.Insets viewportSafe;
    private boolean swiping,accepting,persistent,interactive=true;
    void setPersistent(boolean value) {
        persistent=value;
        findViewById(R.id.queue_close).setVisibility(value?GONE:VISIBLE);
        findViewById(R.id.queue_swap_columns).setVisibility(value?VISIBLE:GONE);
        if(!value)setInteractive(true);
    }
    void setInteractive(boolean value){interactive=value;findViewById(R.id.queue_swap_columns).setEnabled(value);}
    private final Runnable timeout=()->fail(getContext().getString(R.string.queue_start_unconfirmed));
    QueuePanel(Context context,int accent,Runnable interact,Runnable close,Runnable changed) {
        super(context);this.accent=accent;this.interact=interact;this.close=close;this.changed=changed;
        covers=new QueueArtwork(context);
        setVisibility(INVISIBLE);setClickable(true);setFocusable(true);
        column=new LinearLayout(context);column.setOrientation(LinearLayout.VERTICAL);
        addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(context.getString(R.string.queue_title),40,true);title.setAccessibilityHeading(true);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        MaterialButton back=headerButton(R.drawable.ic_arrow_right);
        back.setContentDescription(context.getString(R.string.return_to_player));back.setId(R.id.queue_close);
        back.setOnClickListener(v->{interact.run();close.run();});
        header.addView(back,new LinearLayout.LayoutParams(52,52));
        MaterialButton swap=headerButton(R.drawable.ic_arrows_left_right);swap.setId(R.id.queue_swap_columns);
        swap.setContentDescription(getContext().getString(R.string.swap_columns));swap.setTooltipText(getContext().getString(R.string.swap_columns));swap.setVisibility(GONE);
        swap.setOnClickListener(v->{
            if(!persistent || !interactive || !accepting)return;
            interact.run();swap.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            PlayerPreferences.store(context).edit().putBoolean(PlayerPreferences.INTERNAL_REVERSED,!PlayerPreferences.internalReversed(context)).apply();
        });
        header.addView(swap,new LinearLayout.LayoutParams(52,52));column.addView(header);
        count=text("",13,false);count.setTextColor(Color.rgb(185,191,200));column.addView(count);
        message=text("",12,false);message.setVisibility(GONE);message.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        GradientDrawable messageShape=new GradientDrawable();messageShape.setColor(Color.rgb(48,45,51));messageShape.setCornerRadius(16);message.setBackground(messageShape);
        column.addView(message);
        empty=text(context.getString(R.string.queue_unavailable),20,true);empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        list=new FrostedQueueList(context);list.setId(R.id.queue_list);list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);list.setClipToPadding(false);list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(false);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view,int dx,int dy) {
                if(accepting && (dx!=0 || dy!=0))interact.run();
            }
        });
        addView(list,0,new FrameLayout.LayoutParams(-1,-1));
        headerFrost=new HeaderFrost(context);addView(headerFrost,1,new FrameLayout.LayoutParams(-1,-1));
        addView(empty,2,new FrameLayout.LayoutParams(-1,-1));
    }
    private MaterialButton headerButton(int icon) {
        MaterialButton button=new MaterialButton(getContext(),null,com.google.android.material.R.attr.materialIconButtonFilledTonalStyle);
        button.setIcon(AppCompatResources.getDrawable(getContext(),icon));button.setText("");
        button.setIconTint(android.content.res.ColorStateList.valueOf(accent));button.setIconPadding(0);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);button.setGravity(Gravity.CENTER);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ColorUtils.blendARGB(Color.rgb(35,38,46),accent,.10f)));
        return button;
    }
    private int px(float value) { return Math.round(value*scale); }
    void viewport(int width,int height,float value) {
        if(width<=0 || height<=0)return;
        boolean resized=Math.abs(scale-value)>.01f;
        android.graphics.Insets safe=android.graphics.Insets.NONE;
        if(getRootWindowInsets()!=null)safe=getRootWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
        if(width==viewportWidth && height==viewportHeight && !resized && safe.equals(viewportSafe))return;
        viewportWidth=width;viewportHeight=height;viewportSafe=safe;scale=value;
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)column.getLayoutParams();
        params.width=Math.min(width-px(48),px(420));params.height=FrameLayout.LayoutParams.WRAP_CONTENT;
        params.leftMargin=(width-params.width)/2;params.topMargin=Math.max(safe.top+px(55),px(86));column.setLayoutParams(params);
        FrameLayout.LayoutParams listParams=new FrameLayout.LayoutParams(params.width,Math.max(1,height-safe.bottom-px(16)));
        listParams.leftMargin=params.leftMargin;listParams.topMargin=0;list.setLayoutParams(listParams);
        FrameLayout.LayoutParams emptyParams=new FrameLayout.LayoutParams(params.width,Math.max(1,height-params.topMargin-px(110)-safe.bottom));
        emptyParams.leftMargin=params.leftMargin;emptyParams.topMargin=params.topMargin+px(110);empty.setLayoutParams(emptyParams);
        LinearLayout header=(LinearLayout)column.getChildAt(0);
        ((TextView)header.getChildAt(0)).setTextSize(TypedValue.COMPLEX_UNIT_PX,px(40));
        for(int i=1;i<header.getChildCount();i++) {
            MaterialButton button=(MaterialButton)header.getChildAt(i);
            button.setLayoutParams(new LinearLayout.LayoutParams(px(52),px(52)));button.setIconSize(px(24));button.setCornerRadius(px(26));
            button.setInsetTop(0);button.setInsetBottom(0);button.setMinimumWidth(0);button.setMinimumHeight(0);button.setPadding(0,0,0,0);
        }
        count.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(13));count.setPadding(0,px(5),0,px(22));
        message.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(12));message.setPadding(px(14),px(12),px(14),px(12));
        ((GradientDrawable)message.getBackground()).setCornerRadius(px(18));
        LinearLayout.LayoutParams messageParams=(LinearLayout.LayoutParams)message.getLayoutParams();messageParams.bottomMargin=px(14);message.setLayoutParams(messageParams);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(20));list.setSceneScale(scale);
        if(resized){list.setAdapter(null);list.setAdapter(adapter);}
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        super.onLayout(changed,left,top,right,bottom);
        if(list==null || headerFrost==null)return;
        float extent=column.getBottom()-list.getTop();list.setHeaderExtent(extent);
        headerFrost.setBounds(0,column.getBottom()+px(24));
        int padding=Math.round(extent)+px(28);
        if(list.getPaddingTop()!=padding || list.getPaddingBottom()!=px(52))list.setPadding(0,padding,0,px(52));
    }
    void opened() {
        covers.resume();
        accepting=true;message.setVisibility(GONE);cancelPending();refresh();list.scrollToPosition(0);requestFocus();
        for(int i=0;i<list.getChildCount();i++)((Holder)list.getChildViewHolder(list.getChildAt(i))).loadCover();
    }
    void closed() {
        accepting=false;covers.pause();message.setVisibility(GONE);cancelPending();
        if(list!=null){list.stopScroll();for(int i=0;i<list.getChildCount();i++)((Holder)list.getChildViewHolder(list.getChildAt(i))).cancelCover();}
    }
    boolean pending() { return pending!=null; }
    int itemCount() { return adapter.items.size(); }
    int artworkCached(){return covers.cachedCount();}
    int artworkPending(){return covers.pendingCount();}
    boolean edgeFrost(){return list.frostEnabled();}
    boolean headerFrost(){return list.headerExtent()>0;}
    int thumbnailLoading(){int count=0;for(int i=0;i<list.getChildCount();i++)if(((Holder)list.getChildViewHolder(list.getChildAt(i))).artworkLoading.getVisibility()==VISIBLE)count++;return count;}
    void refresh() {
        if(!accepting || getVisibility()!=VISIBLE)return;
        MusicQueue.Snapshot fresh=MusicQueue.read();
        if(pending!=null) {
            if(!Objects.equals(pendingSession,fresh.token())) { fail(getContext().getString(R.string.queue_session_changed));return; }
            if(MusicQueue.confirmed(fresh,pendingSession,pending)) {
                android.util.Log.i("AmbientQueue","selection_confirmed queueId="+pending.id());
                cancelPending();interact.run();if(!persistent){close.run();return;}
            }
        }
        snapshot=fresh;
        int start=Math.max(0,fresh.current());
        int following=fresh.entries().size()-start-(fresh.current()>=0?1:0);
        count.setText(fresh.entries().isEmpty()?getContext().getString(R.string.queue_no_tracks):fresh.current()>=0
            ?UiText.quantity(getContext(),R.plurals.queue_following,following)
            :UiText.quantity(getContext(),R.plurals.queue_tracks,fresh.entries().size()));
        var rows=new ArrayList<Row>();
        for(int i=start;i<fresh.entries().size();i++) {
            boolean current=i==fresh.current();MusicQueue.Entry entry=fresh.entries().get(i);
            String section=current?getContext().getString(R.string.queue_now_playing):i==start || i==start+1 && fresh.current()>=0?getContext().getString(R.string.queue_up_next):"";
            rows.add(new Row(entry,fresh.token(),current,Objects.equals(pending,entry.key()),fresh.selectable(entry),section,
                current || i==start || i==start+1 && fresh.current()>=0,current || i==fresh.entries().size()-1));
        }
        adapter.update(rows);empty.setVisibility(rows.isEmpty()?VISIBLE:GONE);list.setVisibility(rows.isEmpty()?GONE:VISIBLE);
        changed.run();
    }
    private void select(Row row) {
        if(!interactive || !accepting || pending!=null || getVisibility()!=VISIBLE)return;
        interact.run();message.setVisibility(GONE);
        if(row.current && MusicQueue.confirmed(MusicQueue.read(),row.session,row.entry.key())) { close.run();return; }
        if(!MusicQueue.select(row.session,row.entry)) { fail(getContext().getString(R.string.queue_track_unavailable));return; }
        pending=row.entry.key();pendingSession=row.session;
        performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        handler.postDelayed(timeout,5000);refresh();changed.run();
    }
    private void fail(String reason) {
        cancelPending();
        if(getVisibility()==VISIBLE) {
            // A failed request keeps the real queue visible rather than reporting a false start.
            refresh();message.setText(reason);message.setVisibility(VISIBLE);
        }
        android.util.Log.i("AmbientQueue","selection_unconfirmed");changed.run();
    }
    private void cancelPending() { handler.removeCallbacks(timeout);pending=null;pendingSession=null; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(!interactive)return false;
        if(!accepting)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();swiping=false;interact.run();}
        if(swiping)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE) {
            interact.run();float dx=event.getX()-downX,dy=event.getY()-downY;
            if(!persistent && dx < -px(48) && Math.abs(dx)>Math.abs(dy)*1.4f) {
                // RecyclerView may disallow interception while its vertical fling settles.
                // Observe at dispatch, cancel the child gesture, then perform page navigation.
                MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);cancel.recycle();
                swiping=true;list.stopScroll();close.run();return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) { return true; }
    @Override protected void onDetachedFromWindow() { closed();handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow(); }
    private TextView text(String value,float size,boolean bold) {
        TextView view=new TextView(getContext());view.setText(value);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));
        view.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));view.setTextColor(Color.rgb(238,242,247));
        view.setIncludeFontPadding(false);return view;
    }
    private record Row(MusicQueue.Entry entry,MediaSession.Token session,boolean current,boolean pending,boolean enabled,String section,boolean top,boolean bottom) { }
    private final class Rows extends RecyclerView.Adapter<Holder> {
        private List<Row> items=List.of();
        @android.annotation.SuppressLint("NotifyDataSetChanged") // Initial reveal uses the parent fade; later updates retain precise diffs.
        void update(List<Row> next) {
            List<Row> before=items;
            // The page/column already fades as a whole. Initial rows must not sit at
            // alpha zero waiting for a RecyclerView add animation while it is revealing.
            if(before.isEmpty()){items=List.copyOf(next);notifyDataSetChanged();return;}
            DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback() {
                public int getOldListSize(){return before.size();}public int getNewListSize(){return next.size();}
                public boolean areItemsTheSame(int old,int now){return Objects.equals(before.get(old).session,next.get(now).session) && before.get(old).entry.key().equals(next.get(now).entry.key());}
                public boolean areContentsTheSame(int old,int now){
                    Row a=before.get(old),b=next.get(now);
                    return a.current==b.current && a.pending==b.pending && a.enabled==b.enabled && a.section.equals(b.section)
                        && a.top==b.top && a.bottom==b.bottom && a.entry.title().equals(b.entry.title())
                        && a.entry.artist().equals(b.entry.artist()) && Objects.equals(a.entry.artworkUri(),b.entry.artworkUri())
                        && (a.entry.artwork()==null)==(b.entry.artwork()==null);
                }
            });items=List.copyOf(next);diff.dispatchUpdatesTo(this);
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder();}
        @Override public void onBindViewHolder(Holder holder,int position){holder.bind(items.get(position),position);}
        @Override public void onViewAttachedToWindow(Holder holder){holder.loadCover();}
        @Override public void onViewDetachedFromWindow(Holder holder){holder.cancelCover();}
        @Override public void onViewRecycled(Holder holder){holder.cancelCover();holder.bound=null;holder.artwork.setImageDrawable(null);}
        @Override public int getItemCount(){return items.size();}
    }
    private final class Holder extends RecyclerView.ViewHolder {
        final TextView section,title,artist;
        final MaterialCardView card;
        final FrameLayout thumb,end;
        final ImageView artwork,play;
        final CircularProgressIndicator loading,artworkLoading;
        final LinearLayout line,labels;
        Row bound;
        QueueArtwork.Ticket coverRequest;
        void cancelCover(){if(coverRequest!=null)coverRequest.cancel();coverRequest=null;artworkLoading.setVisibility(INVISIBLE);artwork.animate().cancel();artwork.setAlpha(1f);}
        void loadCover() {
            cancelCover();Row row=bound;
            if(!accepting || row==null || row.entry.artwork()!=null || row.entry.artworkUri()==null)return;
            artwork.setImageDrawable(null);artworkLoading.setVisibility(VISIBLE);changed.run();
            coverRequest=covers.load(row.entry.artworkUri(),(bitmap,cached)->{
                if(!accepting || bound!=row)return;
                artworkLoading.setVisibility(INVISIBLE);
                if(bitmap==null){fallbackCover();changed.run();return;}
                artwork.clearColorFilter();artwork.setPadding(0,0,0,0);artwork.setImageBitmap(bitmap);
                if(!cached && android.animation.ValueAnimator.areAnimatorsEnabled()) {artwork.setAlpha(0f);artwork.animate().alpha(1f).setDuration(180).start();}
                changed.run();
            });
        }
        void fallbackCover(){artwork.setImageResource(R.drawable.ic_music);artwork.setColorFilter(accent);artwork.setPadding(px(14),px(14),px(14),px(14));}
        Holder() {
            super(new LinearLayout(getContext()));
            LinearLayout root=(LinearLayout)itemView;root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            section=text("",11,true);section.setLetterSpacing(.12f);section.setTextColor(accent);root.addView(section);
            card=new MaterialCardView(getContext());card.setCardElevation(0);card.setClickable(true);card.setFocusable(true);
            root.addView(card,new LinearLayout.LayoutParams(-1,-2));
            line=new LinearLayout(getContext());line.setGravity(Gravity.CENTER_VERTICAL);card.addView(line);
            thumb=new FrameLayout(getContext());thumb.setClipToOutline(true);line.addView(thumb);
            artwork=new ImageView(getContext());artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
            artworkLoading=new CircularProgressIndicator(getContext());artworkLoading.setIndeterminate(true);artworkLoading.setIndicatorColor(accent);
            artworkLoading.setIndicatorInset(0);artworkLoading.setVisibility(INVISIBLE);
            thumb.addView(artworkLoading,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
            labels=new LinearLayout(getContext());labels.setOrientation(LinearLayout.VERTICAL);line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            title=text("",17,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);labels.addView(title);
            artist=text("",12,false);artist.setTextColor(Color.rgb(182,190,202));artist.setMaxLines(1);artist.setEllipsize(TextUtils.TruncateAt.END);labels.addView(artist);
            end=new FrameLayout(getContext());line.addView(end);
            play=new ImageView(getContext());play.setImageResource(R.drawable.ic_play);play.setColorFilter(accent);end.addView(play,new FrameLayout.LayoutParams(-1,-1));
            loading=new CircularProgressIndicator(getContext());loading.setIndeterminate(true);loading.setIndicatorColor(accent);loading.setIndicatorInset(0);
            end.addView(loading,new FrameLayout.LayoutParams(-1,-1));loading.setVisibility(INVISIBLE);
        }
        void bind(Row row,int position) {
            cancelCover();bound=row;
            section.setText(row.section);section.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(11));section.setPadding(px(4),position==0?0:px(20),0,px(12));section.setVisibility(row.section.isEmpty()?GONE:VISIBLE);
            var layout=(LinearLayout.LayoutParams)card.getLayoutParams();layout.bottomMargin=px(4);layout.height=px(row.current?94:82);card.setLayoutParams(layout);
            card.setShapeAppearanceModel(ShapeAppearanceModel.builder().setTopLeftCornerSize(px(row.top?28:9)).setTopRightCornerSize(px(row.top?28:9))
                .setBottomLeftCornerSize(px(row.bottom?28:9)).setBottomRightCornerSize(px(row.bottom?28:9)).build());
            card.setCardBackgroundColor(ColorUtils.blendARGB(Color.rgb(26,29,37),accent,row.current?.17f:.045f));
            card.setStrokeColor(row.current?ColorUtils.setAlphaComponent(accent,95):Color.argb(18,255,255,255));card.setStrokeWidth(Math.max(1,px(.6f)));
            line.setPadding(px(14),0,px(14),0);thumb.setLayoutParams(new LinearLayout.LayoutParams(px(54),px(54)));
            GradientDrawable tile=new GradientDrawable();tile.setColor(ColorUtils.blendARGB(Color.rgb(35,39,49),accent,.12f));tile.setCornerRadius(px(18));thumb.setBackground(tile);
            boolean hasArt=row.entry.artwork()!=null && !row.entry.artwork().isRecycled();
            artwork.clearColorFilter();artwork.setImageBitmap(hasArt?row.entry.artwork():null);
            if(!hasArt){fallbackCover();}
            else artwork.setPadding(0,0,0,0);
            artworkLoading.setIndicatorSize(px(24));artworkLoading.setTrackThickness(Math.max(1,px(2)));
            title.setText(row.entry.title(getContext()));title.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(row.current?18:16));
            artist.setText(row.pending?getContext().getString(R.string.queue_starting):row.entry.artist());artist.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(12));artist.setPadding(0,px(5),0,0);
            LinearLayout.LayoutParams labelParams=(LinearLayout.LayoutParams)labels.getLayoutParams();labelParams.leftMargin=px(14);labelParams.rightMargin=px(8);labels.setLayoutParams(labelParams);
            end.setLayoutParams(new LinearLayout.LayoutParams(px(22),px(22)));loading.setIndicatorSize(px(22));loading.setTrackThickness(Math.max(1,px(2)));
            loading.setVisibility(row.pending?VISIBLE:INVISIBLE);play.setVisibility(row.pending || row.current?INVISIBLE:VISIBLE);
            card.setAlpha(row.enabled || row.current?1:.55f);card.setEnabled(pending==null && (row.enabled || row.current));
            card.setContentDescription(getContext().getString(row.current?R.string.queue_playing_description:R.string.queue_play_description,row.entry.title(getContext())+(row.entry.artist().isEmpty()?"":", "+row.entry.artist())));
            card.setOnClickListener(v->select(row));
            if(itemView.isAttachedToWindow())loadCover();
        }
    }
    private final class HeaderFrost extends View {
        private final android.graphics.Paint veil=new android.graphics.Paint();
        private int start,end;
        HeaderFrost(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(true);}
        void setBounds(int top,int bottom) {
            if(start==top && end==bottom)return;start=top;end=bottom;
            veil.setShader(new android.graphics.LinearGradient(0,start,0,end,
                new int[]{Color.argb(180,18,21,27),Color.argb(150,18,21,27),Color.TRANSPARENT},
                new float[]{0,.6f,1},android.graphics.Shader.TileMode.CLAMP));invalidate();
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){canvas.drawRect(0,start,getWidth(),end,veil);}
        @Override public boolean onTouchEvent(MotionEvent event) {
            if(event.getY()<start || event.getY()>end)return false;
            interact.run();return true;
        }
    }
}
