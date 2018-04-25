/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /Users/lilong/Documents/atlas/atlas-update/src/main/aidl/com/taobao/atlas/dexmerge/IDexMergeCallback.aidl
 */
package com.taobao.atlas.dexmerge;
/**
 * Created by xieguo.xg on 1/20/16.
 */
public interface IDexMergeCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements IDexMergeCallback
{
private static final String DESCRIPTOR = "com.taobao.atlas.dexmerge.IDexMergeCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.taobao.atlas.dexmerge.IDexMergeCallback interface,
 * generating a proxy if needed.
 */
public static IDexMergeCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof IDexMergeCallback))) {
return ((IDexMergeCallback)iin);
}
return new Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_onMergeFinish:
{
data.enforceInterface(DESCRIPTOR);
String _arg0;
_arg0 = data.readString();
boolean _arg1;
_arg1 = (0!=data.readInt());
String _arg2;
_arg2 = data.readString();
this.onMergeFinish(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_onMergeAllFinish:
{
data.enforceInterface(DESCRIPTOR);
boolean _arg0;
_arg0 = (0!=data.readInt());
String _arg1;
_arg1 = data.readString();
this.onMergeAllFinish(_arg0, _arg1);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements IDexMergeCallback
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void onMergeFinish(String filePath, boolean result, String reason) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(filePath);
_data.writeInt(((result)?(1):(0)));
_data.writeString(reason);
mRemote.transact(Stub.TRANSACTION_onMergeFinish, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onMergeAllFinish(boolean result, String reason) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(((result)?(1):(0)));
_data.writeString(reason);
mRemote.transact(Stub.TRANSACTION_onMergeAllFinish, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_onMergeFinish = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_onMergeAllFinish = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void onMergeFinish(String filePath, boolean result, String reason) throws android.os.RemoteException;
public void onMergeAllFinish(boolean result, String reason) throws android.os.RemoteException;
}
