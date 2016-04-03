package me.devsaki.hentoid.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;

import com.github.paolorotolo.appintro.AppIntro2;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.BaseSlide;
import me.devsaki.hentoid.util.AndroidHelper;

/**
 * Created by avluis on 03/20/2016.
 * Welcome (Intro Slide) Activity
 * Presents required permissions, then calls the proper activity to:
 * Set storage directory and library import
 */
public class IntroSlideActivity extends AppIntro2 {
    private int importSlide = 4;

    @Override
    public void init(@Nullable Bundle savedInstanceState) {
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_01));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02));
            // Ask Storage permission in the second slide,
            // but only for Android M+ users.
            askForPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        } else {
            // In order to keep the number of slides the same,
            // we show this info slide for non-M users.
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02_alt));
        }
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_03));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_04));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_05));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_06));

        setVibrate(true);
        setVibrateIntensity(30);
    }

    @Override
    public void onNextPressed() {
        // Do something when users tap on Next button.
    }

    @Override
    public void onDonePressed() {
        AndroidHelper.commitFirstRun(true);
        AndroidHelper.launchMainActivity(this);
        finish();
    }

    @Override
    public void onSlideChanged() {
        // Show the import activity just prior to the last slide
        if (pager.getCurrentItem() == importSlide) {
            setProgressButtonEnabled(false);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                public void run() {
                    Intent selectFolder = new Intent(
                            getApplicationContext(), ImportActivity.class);
                    startActivityForResult(selectFolder, 1);
                }
            }, 100);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            setProgressButtonEnabled(true);
            String result = data.getStringExtra("result");
            if (resultCode == Activity.RESULT_OK) {
                // If we get RESULT_OK, then:
                System.out.println("RESULT_OK: ");
                System.out.println(result);

                // If result passes validation, then we move to next slide
                pager.setCurrentItem(importSlide + 1);
                // Disallow swiping back
                setSwipeLock(true);

            }
            if (resultCode == Activity.RESULT_CANCELED) {
                switch (result) {
                    case "PERMISSION_DENIED":
                        System.out.println("Permission Denied by User");

                        // If we get PERMISSION_DENIED, then go back 3 slides
                        pager.setCurrentItem(importSlide - 3);
                        AndroidHelper.singleSnack(pager,
                                getString(R.string.permission_denied),
                                Snackbar.LENGTH_LONG);
                        break;
                    case "PERMISSION_DENIED_FORCED":
                        System.out.println("Permission Denied (Forced) by User/Policy");

                        setProgressButtonEnabled(false);
                        setSwipeLock(true);
                        pager.setCurrentItem(importSlide - 3);
                        AndroidHelper.singleSnack(pager,
                                getString(R.string.permission_denied_forced),
                                Snackbar.LENGTH_INDEFINITE);

                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                finish();
                            }
                        }, 5000);
                        break;
                    default:
                        System.out.println("RESULT_CANCELED");

                        // If we get RESULT_CANCELED, then go back 2 slides
                        pager.setCurrentItem(importSlide - 2);
                        break;
                }
            }
        }
    }
}