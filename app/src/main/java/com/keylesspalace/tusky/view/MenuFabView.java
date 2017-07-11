package com.keylesspalace.tusky.view;

import android.animation.Animator;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.MenuFabViewListener;

/**
 * Created by torrentcome on 10/07/2017.
 * custom fab menu layout
 */

public class MenuFabView extends RelativeLayout {

    private boolean isFABOpen = false;
    private FloatingActionButton fab1, fab2;
    private LinearLayout fabLayout1, fabLayout2;
    private View fabBackground;
    private MenuFabViewListener handler;

    public MenuFabView(Context context) {
        super(context);
        if (!isInEditMode())
            init();
    }

    public MenuFabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode())
            init();
    }

    public MenuFabView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode())
            init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public MenuFabView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (!isInEditMode())
            init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_menu_fab, this);

        fab1 = (FloatingActionButton) findViewById(R.id.fab1);
        fab2 = (FloatingActionButton) findViewById(R.id.fab2);
        fabLayout1 = (LinearLayout) findViewById(R.id.fabLayout1);
        fabLayout2 = (LinearLayout) findViewById(R.id.fabLayout2);
        fabBackground = findViewById(R.id.fabbackground);
    }

    public void attachView(Context context, View v) {
        if (context == null) {
            Log.e("error", "handler null");
        } else {
            this.handler = (MenuFabViewListener) context;
        }
        if (v != null) {
            v.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (!isFABOpen) {
                        showFABMenu();
                    } else {
                        closeFABMenu();
                    }
                    return true;
                }
            });

            fabBackground.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    closeFABMenu();
                }
            });
            fab1.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    handler.menuFabSaveToot();
                }
            });
            fab2.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    handler.menuFabCopy();
                }
            });
        }
    }

    private void showFABMenu() {
        isFABOpen = true;
        fabBackground.setVisibility(View.VISIBLE);

        //1
        fabLayout1.setVisibility(View.VISIBLE);
        fabLayout1.animate().translationY(-getResources().getDimension(R.dimen.standard_50));
        fabLayout1.animate().alpha(1);
        //2
        fabLayout2.setVisibility(View.VISIBLE);
        fabLayout2.animate().translationY(-getResources().getDimension(R.dimen.standard_100));
        fabLayout2.animate().alpha(1);
    }

    public void closeFABMenu() {
        isFABOpen = false;
        fabBackground.setVisibility(View.GONE);

        //1
        fabLayout1.animate().alpha(0);
        fabLayout1.animate().translationY(0);
        //2
        fabLayout2.animate().alpha(0);
        fabLayout2.animate().translationY(0).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (!isFABOpen) {
                    fabLayout1.setVisibility(View.GONE);
                    fabLayout2.setVisibility(View.GONE);
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
    }

    public boolean isFABOpen() {
        return isFABOpen;
    }
}
