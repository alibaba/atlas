# Atlas Contributing Guide

Welcome to create [Pull Requests](https://github.com/alibaba/atlas/compare) or open [Issues](https://github.com/alibaba/atlas/issues/new) for bugfix, doc, example, suggestion and anything.

## Branch Management

```
master
 ↑
dev         <--- PR(hotfix/typo/3rd-PR)
 ↑ PR
{domain}-feature-{date}
```  
Atlas Branches

0. `master` branch
    0. `master` is the latest (pre-)release branch.
0. `dev` branch
    0. `dev` is the stable developing branch. [Github Release](https://help.github.com/articles/creating-releases/) is used to publish a (pre-)release version to `master` branch.
    0. ***It's RECOMMENDED to commit hotfix (like typo) or feature PR to `dev`***.
0. `{domain}-feature-{date}` branch
    0. The branch for a developing iteration, e.g. `core-feature-20170118` is an atlas-core developing iteration which is done at 2017.01.18. `{domain}` consists of `core`, `update`, `plugin` and `aapt`. 
    0. **DO NOT commit any PR to such a branch**.

### Branch Name 

```
{module}-{action}-{shortName}
```

* `{module}`, see [commit log module](#commit-log)
* `{action}`
    * `feature`: checkout from `{module}` and merge to `{module}` later. If `{module}` not exists, merge to `dev`
    * `bugfix`: like `feature`, for bugfix only
    * `hotfix`: checkout from `master` or release `tag`, merge to `master` and `{module}` later. If `{module}` not exists, merge to `dev`

for example:

* `core-bugfix-memory`
* `plugin-feature-redex`
* `update-hotfix-sequence`

## Commit Log

```
[{module}] {description}
```
* `{module}`
    * Including: atlas-core, atlas-aapt, atlas-update, atlas-gradle-plugin, atlas-doc, atlas-website, atlas-demo, test, all 
* `{description}`
    * It's ***RECOMMENDED*** to close issue with syntax `close #66` or `fix #66`, see [the doc](https://help.github.com/articles/closing-issues-via-commit-messages/) for more detail. It's useful for responding issues.

for example:

* `[all] close #66, add refreshing for memory`
* `[atlas-doc] fix #68, update some instruction`
* `[atlas-demo] remove abc`


## Pull Request

[Create Pull Requests](https://github.com/alibaba/atlas/compare).

## Contributor License Agreement
In order to contribute code to Atlas, you (or the legal entity you represent) must sign the Contributor License Agreement (CLA).

You can read and sign the [Alibaba CLA](https://cla-assistant.io/alibaba/atlas) online.

For CLA assistant service works properly, please make sure you have added email address that your commits linked to GitHub account.

Please read [How to setting your Email address in Git](https://help.github.com/articles/setting-your-email-in-git/) and [How to adding an email address to your GitHub Account](https://help.github.com/articles/adding-an-email-address-to-your-github-account/).
