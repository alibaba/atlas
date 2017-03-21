package com.middleware.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.taobao.middleware.R;

public class Dialog extends android.app.Dialog {

    Context context;
    View view;
    View backView;
    String message;
    TextView messageTextView;
    String title;
    TextView titleTextView;

    ButtonFlat buttonAccept;
    ButtonFlat buttonCancel;

    String buttonCancelText;
    String buttonAcceptText;

    View.OnClickListener onAcceptButtonClickListener;
    View.OnClickListener onCancelButtonClickListener;

    boolean clickBackViewToExit = true;

    public Dialog(Context context, String title, String message) {
        super(context, android.R.style.Theme_Translucent);
        this.context = context;// init Context
        this.message = message;
        this.title = title;
    }

    public Dialog(Context context, String title, String message, boolean clickBackViewToExit) {
        super(context, android.R.style.Theme_Translucent);
        this.context = context;// init Context
        this.message = message;
        this.title = title;
        this.clickBackViewToExit = clickBackViewToExit;
    }

    public void addCancelButton(String buttonCancelText) {
        this.buttonCancelText = buttonCancelText;
        if (buttonCancel != null) {
            buttonCancel.setText(buttonCancelText);
        }
    }

    public void addCancelButton(String buttonCancelText, View.OnClickListener onCancelButtonClickListener) {
        this.buttonCancelText = buttonCancelText;
        this.onCancelButtonClickListener = onCancelButtonClickListener;
    }

    public void addAcceptButton(String buttonAcceptText) {
        this.buttonAcceptText = buttonAcceptText;
        if (buttonAccept != null) {
            buttonAccept.setText(buttonAcceptText);
        }
    }

    public void addAcceptButton(String buttonAcceptText, View.OnClickListener onAcceptButtonClickListener) {
        this.buttonAcceptText = buttonAcceptText;
        this.onAcceptButtonClickListener = onAcceptButtonClickListener;
    }

    private View mCustomContentView;

    @Override
    public void setContentView(View view) {
        mCustomContentView = view;
    }

    public View getContentView() {
        return mCustomContentView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        View dialogContent = LayoutInflater.from(getContext()).inflate(R.layout.update_dialog, null);
        super.setContentView(dialogContent);

        view = (RelativeLayout) findViewById(R.id.update_contentDialog);
        backView = (FrameLayout) findViewById(R.id.update_dialog_rootView);
        backView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getX() < view.getLeft()
                        || event.getX() > view.getRight()
                        || event.getY() > view.getBottom()
                        || event.getY() < view.getTop()) {

                    if (clickBackViewToExit) {
                        if (null != onCancelButtonClickListener) {
                            onCancelButtonClickListener.onClick(buttonCancel);
                        }
                        dismiss();
                    }

                }
                return false;
            }
        });

        this.titleTextView = (TextView) findViewById(R.id.update_title);
        setTitle(title);

        if (mCustomContentView != null) {
            FrameLayout dialogContentFrame = (FrameLayout) findViewById(R.id.update_dialog_content);
            dialogContentFrame.addView(mCustomContentView);
            findViewById(R.id.message_scrollView).setVisibility(View.GONE);

        } else {
            this.messageTextView = (TextView) findViewById(R.id.update_message);
            setMessage(message);
        }

//        this.buttonAccept = (ButtonFlat) findViewById(R.id.button_accept);
//        buttonAccept.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                dismiss();
//                if(onAcceptButtonClickListener != null)
//                    onAcceptButtonClickListener.onClick(v);
//            }
//        });

        if (buttonCancelText != null) {
            this.buttonCancel = (ButtonFlat) findViewById(R.id.update_button_cancel);
            this.buttonCancel.setVisibility(View.VISIBLE);
            this.buttonCancel.setText(buttonCancelText);
            buttonCancel.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    dismiss();
                    if (onCancelButtonClickListener != null)
                        onCancelButtonClickListener.onClick(v);
                }
            });
        }

        if (buttonAcceptText != null) {
            this.buttonAccept = (ButtonFlat) findViewById(R.id.update_button_accept);
            this.buttonAccept.setVisibility(View.VISIBLE);
            this.buttonAccept.setText(buttonAcceptText);
            buttonAccept.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    dismiss();
                    if (onAcceptButtonClickListener != null)
                        onAcceptButtonClickListener.onClick(v);
                }
            });
        }

    }

    @Override
    public void show() {
        super.show();
        // set update_dialog enter animations
        view.startAnimation(AnimationUtils.loadAnimation(context.getApplicationContext(), R.anim.dialog_main_show_amination));
        backView.startAnimation(AnimationUtils.loadAnimation(context.getApplicationContext(), R.anim.dialog_root_show_amin));
    }

    // GETERS & SETTERS

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        messageTextView.setText(message);
    }

    public TextView getMessageTextView() {
        return messageTextView;
    }

    public void setMessageTextView(TextView messageTextView) {
        this.messageTextView = messageTextView;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        if (title == null)
            titleTextView.setVisibility(View.GONE);
        else {
            titleTextView.setVisibility(View.VISIBLE);
            titleTextView.setText(title);
        }
    }

    public TextView getTitleTextView() {
        return titleTextView;
    }

    public void setTitleTextView(TextView titleTextView) {
        this.titleTextView = titleTextView;
    }

    public ButtonFlat getButtonAccept() {
        return buttonAccept;
    }

    public void setButtonAccept(ButtonFlat buttonAccept) {
        this.buttonAccept = buttonAccept;
    }

    public ButtonFlat getButtonCancel() {
        return buttonCancel;
    }

    public void setButtonCancel(ButtonFlat buttonCancel) {
        this.buttonCancel = buttonCancel;
    }

    public void setOnAcceptButtonClickListener(
            View.OnClickListener onAcceptButtonClickListener) {
        this.onAcceptButtonClickListener = onAcceptButtonClickListener;
        if (buttonAccept != null)
            buttonAccept.setOnClickListener(onAcceptButtonClickListener);
    }

    public void setOnCancelButtonClickListener(
            View.OnClickListener onCancelButtonClickListener) {
        this.onCancelButtonClickListener = onCancelButtonClickListener;
        if (buttonCancel != null)
            buttonCancel.setOnClickListener(onCancelButtonClickListener);
    }

    @Override
    public void dismiss() {
        Animation anim = AnimationUtils.loadAnimation(context.getApplicationContext(), R.anim.dialog_main_hide_amination);
        anim.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        Dialog.super.dismiss();
                    }
                });

            }
        });
        Animation backAnim = AnimationUtils.loadAnimation(context.getApplicationContext(), R.anim.dialog_root_hide_amin);

        view.startAnimation(anim);
        backView.startAnimation(backAnim);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (onCancelButtonClickListener != null)
            onCancelButtonClickListener.onClick(null);
    }
}