package com.taobao.checker;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * @author lilong
 * @create 2017-08-15 下午1:18
 */

public interface Checker {

     List<ReasonMsg> check() throws IOException;

    public enum ReasonType {
        SUCCESS("校验成功"),
        ERROR1("patch包中bundle个数和json中不匹配"),
        ERROR2("动态部署的bundle srcunittag和unittag相同"),
        ERROR3("对应的patch包未打出"),
        ERROR4("unittag变化了但是不在patch包中"),
        ERROR5("FrameworkProperties中的unittag和patch信息中的不一致"),
        ERROR6("FrameworkProperties中unittag发生了变化,但是patch中不存在"),
        ERROR7("FrameworkProperties中unittag没有发生变化,但是patch中有这个bundle"),
        ERROR8("patch中的srcunittag和历史的bundle匹配不上"),
        ERROR9("TEST");



        public String getMsg() {
            return msg;
        }

        private String msg;

        ReasonType(String s) {
            this.msg = s;
        }

    }
    public class ReasonMsg implements Serializable {
        private ReasonType reasonType;
        private String msg;

        public ReasonMsg(ReasonType reasonType,String msg) {
            this.reasonType = reasonType;
            this.msg = msg;
        }

        @Override
        public String toString() {
            return reasonType.getMsg() + msg;
        }
    }

}
