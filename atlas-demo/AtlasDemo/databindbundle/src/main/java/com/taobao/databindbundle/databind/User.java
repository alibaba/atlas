package com.taobao.databindbundle.databind;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

import com.taobao.databindbundle.BR;

/**
 * Created by wuzhong on 2016/10/19.
 */

public class User extends BaseObservable {

    String firstName;
    String lastName;

    public User(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Bindable
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        this.notifyPropertyChanged(BR.firstName);
    }

    @Bindable
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
