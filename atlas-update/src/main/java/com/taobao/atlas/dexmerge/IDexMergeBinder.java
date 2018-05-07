/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /Users/lilong/Documents/atlas/atlas-update/src/main/aidl/com/taobao/atlas/dexmerge/IDexMergeBinder.aidl
 */
package com.taobao.atlas.dexmerge;
/**
 * Created by xieguo.xg on 1/20/16.
 */
public interface IDexMergeBinder extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements IDexMergeBinder
{
private static final String DESCRIPTOR = "com.taobao.atlas.dexmerge.IDexMergeBinder";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.taobao.atlas.dexmerge.IDexMergeBinder interface,
 * generating a proxy if needed.
 */
public static IDexMergeBinder asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof IDexMergeBinder))) {
return ((IDexMergeBinder)iin);
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
case TRANSACTION_dexMerge:
{
data.enforceInterface(DESCRIPTOR);
String _arg0;
_arg0 = data.readString();
java.util.List<MergeObject> _arg1;
_arg1 = data.createTypedArrayList(MergeObject.CREATOR);
boolean _arg2;
_arg2 = (0!=data.readInt());
this.dexMerge(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_registerListener:
{
data.enforceInterface(DESCRIPTOR);
IDexMergeCallback _arg0;
_arg0 = IDexMergeCallback.Stub.asInterface(data.readStrongBinder());
this.registerListener(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements IDexMergeBinder
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
@Override public void dexMerge(String patchFilePath, java.util.List<MergeObject> toMergeList, boolean diffBundleDex) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(patchFilePath);
_data.writeTypedList(toMergeList);
_data.writeInt(((diffBundleDex)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_dexMerge, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void registerListener(IDexMergeCallback listener) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((listener!=null))?(listener.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_registerListener, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_dexMerge = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_registerListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void dexMerge(String patchFilePath, java.util.List<MergeObject> toMergeList, boolean diffBundleDex) throws android.os.RemoteException;
public void registerListener(IDexMergeCallback listener) throws android.os.RemoteException;
}
