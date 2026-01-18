# MMS Doorbell Project
The goal of this project is to provide users of the Blaster Design Factory an easy way of checking current operation status as well as providing usage statistics and insights to all members.

## Modules
The MMS Doorbell Project consists of two programmed modules and one physical module. 
1. Source code for the main Discord application hosted on a Raspbery Pi is located in [src](./src).
2. Source code for the auto-updater is located in the Gradle module at [MMSBotUpdater](./MMSBotUpdater).
3. All CAD files for are stored in the [SOLIDWORKS](./SOLIDWORKS) directory.

## Important Information for future maintainers
### Account Management
The Discord account which owns all MMS club applications is to be passed on to each successive Webmaster upon election of a new officer. Its username is `@mms_bots`, and it uses the Mines Maker Society club email, with the added `+bots` tag: `makers+bots@mines.edu`.

### Token Handling/Security
The Discord bot token is not to be included in ANY source code, and should *only* be stored in the configuration file in the Raspberry Pi. The token is to be rotated every time a new Webmaster is elected, or whenever needed to maintain security.

To minimize the risk of damage if the token is leaked, the bot account on Discord should have the minimal permissions needed for operation. It currently needs the following access:
- Serve Application Commands
- Manage roles (on role)
    - Its role is to be placed at the bottom, with only roles it should be allowed to give below it.
- Send Messages (on role)
- Attach Files (on role)
- Mention @everyone, @here, and all Roles (on role)
- Send Messages (on status channel)
- Delete Messages (on status channel)