Short description of usage:

```shell
make clean test
```

It presumes you have at least a working java on your machine and Make of course.

It takes care about:
  * getting gradle
  * building the project
  * downloading ilivalidator
  * use the model and xtf in the test folder to validate it with the created plugin

If you have gradle available you can create a gradle wrapper and build with this.

Then you need to get your copy of ilivalidator manually.
