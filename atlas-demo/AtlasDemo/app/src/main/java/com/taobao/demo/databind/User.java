package com.taobao.demo.databind;


import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

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
        this.notifyPropertyChanged(com.taobao.databindbundle.BR.firstName);
    }

    @Bindable
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
