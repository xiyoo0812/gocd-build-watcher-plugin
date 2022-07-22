# gocd-build-watcher-plugin
A GoCD Notification plugin which sends emails and WebHook messages to team group

## GoCD Configuration
* 打开插件页面
* 配置GoCD的服务器地址
* 配置GoCD的账号和密码

## Email Configuration
* 打开插件页面
* 配置SMTP邮件服务器相关参数

## Webhook Configuration
* 打开pipeline的配置页面
* 在参数页面配置 `emails` 域，多个邮件使用 `,` 分割，pipeline结束后会抄送通知给配置的邮件地址。
* 目前支持企业微信、钉钉、飞书三种webhook
* 在参数页面配置 `wechat` 域，pipeline结束后会抄送通知给配置的企业微信。
* 在参数页面配置 `dingding` 域，pipeline结束后会抄送通知给配置的钉钉。
* 在参数页面配置 `lark` 域，pipeline结束后会抄送通知给配置的飞书。

## Installation
To install the plugin you must first clone and build the repository.  Once built:
1. Copy \plugin\build\libs\build-watcher-plugin-<version>.jar to \<gocd-installation-folder>\plugins\external

# Development
This repository is forked from: https://github.com/gmazzo/gocd-build-watcher-plugin

Modifications:
1. Uses gradle 6.8.
2. Targets GoCD 2.1.2.
3. Changes regex to extract emails from the git usernme.
4. webhook.

### Building

To build the repository:
    `./gradlew assemble`

To clean and rebuild:
    `./gradlew clean assemble`
