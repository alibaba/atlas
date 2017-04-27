package com.taobao.databindbundle.databind;

import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import com.taobao.databindbundle.R;
import com.taobao.databindbundle.databinding.BundleDatabindMainBinding;


public class DataBundleSampleActivity extends Activity {
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.bundle_databind_main);

        TextView textView = (TextView) findViewById(R.id.xxxxx);

        BundleDatabindMainBinding binding = DataBindingUtil.setContentView(this, R.layout.bundle_databind_main);
        final User user = new User("Test", "User");
        binding.setUser(user);

        EditText editText = (EditText) findViewById(R.id.inputText);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                System.out.println(s);
                user.setFirstName(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });


    }
}
