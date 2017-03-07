# atlas-aapt

This project base on [ShakaAapt](https://github.com/rover12421/ShakaAapt)，which strip the necessary aapt code from AOSP android-7.0.0_r6 branch.

## Compile Env

Please refer to AOSP

## atlas-aapt

atlas-aapt expand follow options base on AOSP aapt:

1. --customized-package-id: generally aapt typed package resource id is 0x7f, this is the application bundle package-id of resources. Use this option to specify the resource package-id for other values, but must be less than or equal to 0x7f. (example: - customized package - id 126).

2. --use-skt-package-name: generally a package reference another package of resources, resources must be combined with the package name for reference, such as a reference when android resource must be combined with '@ android:' for reference. Use this option, can eliminate the process of writing a package name, find resources directly. The name of this package is usually the main atlas package name.

3. -B: used for dynamic deployment of resources, if you want to hit this package based on the a package of resources patch package, so use this option to specify the basic package.