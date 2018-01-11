package com.taobao.databinding.library;
import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import com.taobao.databindlibrary.R;
import com.taobao.databindlibrary.databinding.AarDatabindBinding;



public class DataBingAarActivity extends Activity {
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.aar_databind);

        TextView textView = (TextView) findViewById(R.id.library_aar);

        AarDatabindBinding binding = DataBindingUtil.setContentView(this, R.layout.aar_databind);
        final User user = new User("Test", "User");
        binding.setUser(user);

        EditText editText = (EditText) findViewById(R.id.inputText_library_aar);
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
