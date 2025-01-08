Files Overview
LICENSE.md
README.md
USER-AGREEMENT.md - By using the plugin you agree to User Agreement


Covered Files
This document lists the files in the HxAuctionHouse plugin, along with the third-party dependencies.

Original Files:
Java Classes:
HxAuction.java: Main plugin class.
commands/AuctionCommand.java: Command handler for auction actions.
commands/AuctionTabCompleter.java: Tab completion for commands.
gui/AuctionGUI.java: Main auction GUI.
gui/BrowseGUI.java: GUI for browsing auction items.
gui/ConfirmGUI.java: Confirmation screen for actions.
gui/SellGUI.java: Selling interface.
managers/AuctionManager.java: Core auction logic.
managers/DatabaseManager.java: Handles database interactions.
managers/ConfigManager.java: Manages configuration settings.
models/AuctionItem.java: Represents auction items.
models/AuctionListing.java: Represents auction listings.
events/AuctionBuyEvent.java: Custom buy event.
events/AuctionSellEvent.java: Custom sell event.
events/AuctionExpireEvent.java: Custom expire event.
ItemSerializer.java: Handles item serialization.
EconomyUtil.java: Manages economy interactions.
MessageUtil.java: Utility for message formatting.
Configuration Files:
plugin.yml: Plugin metadata.
config.yml: Main configuration.
actions.yml: Customizable action messages.

Third-Party Dependencies:
Vault (economy): Required for economy system integration. Vault License
Spigot API: Used to interact with Minecraft servers.
By using this plugin, you agree to the respective terms of service and licenses for these dependencies.

ALL THE FILES ARE IN THE GITHUB REPOSITORY: https://github.com/JavaValentin/HxAuctionHouse