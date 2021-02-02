# Add Trace SDK Android

## Overview

Ensures that Trace is added to a given Android application project. Adds the required
dependency (trace-sdk) and applies plugin (trace-gradle-plugin) if project does not already have 
them. This step is needed, when you have not added Trace manually to your project. For manual
installation see the documentation below.

**Use this step before your application is built/assembled with Gradle**

For the known limitations of trace-android-sdk please see the 
[README.md](https://github.com/bitrise-io/trace-android-sdk/blob/main/README.md) file.

## Tech details

This step is a hybrid Go-Java step. Initially it will do the following:

* Create/add to the Android project's buildSrc a Gradle task called InjectTraceTask

* Append this task to the root build.gradle

* Runs that task

* The task will check your dependencies, ensures 'trace-sdk' is a dependency and 'trace-gradle-plugin' 
is applied as a plugin

* If needed, will add the dependencies and apply the plugin on your build.gradle files

## Documentation

www.bitrise.io:
[https://devcenter.bitrise.io/monitoring/getting-started-with-trace/](https://devcenter.bitrise.io/monitoring/getting-started-with-trace/)

## About Trace

**Trace:** [https://trace.bitrise.io](https://trace.bitrise.io)

**What's Trace?** [https://www.bitrise.io/add-ons/trace-mobile-monitoring](https://www.bitrise.io/add-ons/trace-mobile-monitoring)

**Getting started guide:** [https://trace.bitrise.io/o/getting-started](https://trace.bitrise.io/o/getting-started)

## iOS

**For iOS, please check this step:**
[https://www.bitrise.io/integrations/steps/add-trace-sdk](https://www.bitrise.io/integrations/steps/add-trace-sdk)

**Source for iOS step:**
[https://github.com/bitrise-steplib/bitrise-step-add-trace-sdk](https://github.com/bitrise-steplib/bitrise-step-add-trace-sdk)

## How to use this Step

Can be run directly with the [bitrise CLI](https://github.com/bitrise-io/bitrise),
just `git clone` this repository, `cd` into it's folder in your Terminal/Command Line
and call `bitrise run test`.

*Check the `bitrise.yml` file for required inputs which have to be
added to your `.bitrise.secrets.yml` file!*

Step by step:

1. Open up your Terminal / Command Line
2. `git clone` the repository
3. `cd` into the directory of the step (the one you just `git clone`d)
5. Create a `.bitrise.secrets.yml` file in the same directory of `bitrise.yml`
   (the `.bitrise.secrets.yml` is a git ignored file, you can store your secrets in it)
6. Check the `bitrise.yml` file for any secret you should set in `.bitrise.secrets.yml`
  * Best practice is to mark these options with something like `# define these in your .bitrise.secrets.yml`, in the `app:envs` section.
7. Once you have all the required secret parameters in your `.bitrise.secrets.yml` you can just run this step with the [bitrise CLI](https://github.com/bitrise-io/bitrise): `bitrise run test`

An example `.bitrise.secrets.yml` file:

```
envs:
- A_SECRET_PARAM_ONE: the value for secret one
- A_SECRET_PARAM_TWO: the value for secret two
```

## How to create your own step

1. Create a new git repository for your step (**don't fork** the *step template*, create a *new* repository)
2. Copy the [step template](https://github.com/bitrise-steplib/step-template) files into your repository
3. Fill the `step.sh` with your functionality
4. Wire out your inputs to `step.yml` (`inputs` section)
5. Fill out the other parts of the `step.yml` too
6. Provide test values for the inputs in the `bitrise.yml`
7. Run your step with `bitrise run test` - if it works, you're ready

__For Step development guidelines & best practices__ check this documentation: [https://github.com/bitrise-io/bitrise/blob/master/_docs/step-development-guideline.md](https://github.com/bitrise-io/bitrise/blob/master/_docs/step-development-guideline.md).

**NOTE:**

If you want to use your step in your project's `bitrise.yml`:

1. git push the step into it's repository
2. reference it in your `bitrise.yml` with the `git::PUBLIC-GIT-CLONE-URL@BRANCH` step reference style:

```
- git::https://github.com/user/my-step.git@branch:
   title: My step
   inputs:
   - my_input_1: "my value 1"
   - my_input_2: "my value 2"
```

You can find more examples of step reference styles
in the [bitrise CLI repository](https://github.com/bitrise-io/bitrise/blob/master/_examples/tutorials/steps-and-workflows/bitrise.yml#L65).

## How to contribute to this Step

1. Fork this repository
2. `git clone` it
3. Create a branch you'll work on
4. To use/test the step just follow the **How to use this Step** section
5. Do the changes you want to
6. Run/test the step before sending your contribution
  * You can also test the step in your `bitrise` project, either on your Mac or on [bitrise.io](https://www.bitrise.io)
  * You just have to replace the step ID in your project's `bitrise.yml` with either a relative path, or with a git URL format
  * (relative) path format: instead of `- original-step-id:` use `- path::./relative/path/of/script/on/your/Mac:`
  * direct git URL format: instead of `- original-step-id:` use `- git::https://github.com/user/step.git@branch:`
  * You can find more example of alternative step referencing at: https://github.com/bitrise-io/bitrise/blob/master/_examples/tutorials/steps-and-workflows/bitrise.yml
7. Once you're done just commit your changes & create a Pull Request


## Share your own Step

You can share your Step or step version with the [bitrise CLI](https://github.com/bitrise-io/bitrise). If you use the `bitrise.yml` included in this repository, all you have to do is:

1. In your Terminal / Command Line `cd` into this directory (where the `bitrise.yml` of the step is located)
1. Run: `bitrise run test` to test the step
1. Run: `bitrise run audit-this-step` to audit the `step.yml`
1. Check the `share-this-step` workflow in the `bitrise.yml`, and fill out the
   `envs` if you haven't done so already (don't forget to bump the version number if this is an update
   of your step!)
1. Then run: `bitrise run share-this-step` to share the step (version) you specified in the `envs`
1. Send the Pull Request, as described in the logs of `bitrise run share-this-step`

That's all ;)
