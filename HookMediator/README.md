# HookMediator

Wraps various OemHook interfaces and deals with concurrent access.

Applications can link against the static java library `HookMediator_aidl-java` or `HookMediator_aidl-cpp`, then use the bind the service and use the `IHooks` interface as usual:

```kotlin
val intent = Intent()
val pkg = IHooks::class.java.getPackage()!!.name
intent.setClassName(pkg, "$pkg.HookMediatorService")
val connection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
        val hooks = IHooks.Stub.asInterface(service)
        // Use AIDL functions as usual
    }

    override fun onServiceDisconnected(className: ComponentName?) {
    }
}
if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
    throw RuntimeException("Failed to bind IHooks service!")
```

```java
final Intent intent = new Intent();
final String pkg = IHooks.class.getPackage().getName();
intent.setClassName(pkg, pkg + ".HookMediatorService");
ServiceConnection connection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
        IHooks hooks = IHooks.Stub.asInterface(service);
        // Use AIDL functions as usual
    }

    public void onServiceDisconnected(ComponentName className) {
    }
};
if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
    throw new RuntimeException("Failed to bind IHooks service!");
```
