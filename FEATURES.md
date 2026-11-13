# MZGram features

What MZGram adds to the regular Telegram apps, and how it differs from them. MZGram is an unofficial client: MZGram for Android is based on Telegram for Android, MZGram Desktop on Telegram Desktop. Everything here is in the code today; nothing planned is listed.

"Since" is the first MZGram release with the feature. "Next release" means it is done and tested but not released yet (Android 1.0.11, Desktop 1.0.11).

Most switches are under **Settings > MZGram**, grouped into topic pages. This file is the same in both repositories; it is updated together with every change to a feature.

- [Android](#android)
- [Desktop](#desktop)
- [Android and Desktop compared](#android-and-desktop-compared)

## Android

### Message archive

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Deleted messages are kept | In every private chat, group, channel and secret chat, another person's message stays in the chat after they delete it, from a local copy. Telegram removes it. Your own messages are never kept. On by default. | 1.0.0 |
| Edit history | Every earlier version of another person's edited message is kept; the message history screen shows the revisions, and a tap copies one. Telegram shows only the last version. | 1.0.0 |
| View-once media kept | View-once photos and videos are kept with their file, also unopened ones. Deleted messages and removed media also stay in topics, threads and secret chats. | 1.0.0 |
| Chats with only deleted messages stay | When the other side deletes the only message of a chat or its whole history (also by clearing it), the chat stays in the chat list and shows the newest kept message, also after a restart. Telegram removes such a chat. Without kept messages the chat goes as usual. | 1.0.4 |
| Archive switches | Separate switches for deleted messages, edit history, media files, formatting and reactions. | 1.0.0 |
| Keep messages from | Four switches, all on by default: from users, from bots, in groups, in channels. A bot's message follows From bots in any chat; other messages follow the kind of chat, and a channel writing in a group or an anonymous admin counts as the group. Turning one off stops new saves; what is kept stays. | 1.0.9 |
| Archive size | The Archive topic starts with the archive's size as Telegram's Storage Usage shows it: the total, a bar split by kind and a legend with the messages database, the media, Saved Attachments and other files. It is counted in the background and shows 0 right after Clear archive, which also gives the database's space back. | 1.0.9 |
| Download in advance | Settings > MZGram > Archive > Download in advance. Every message from someone else is kept as it comes, with what is needed to download its file later, also when Telegram's own cache never had it. Its file is downloaded at once up to a limit for each kind of chat: users 300 MB, bots 5 MB, groups 20 MB, channels 5 MB, or No limit. When the message is deleted, a file already on the device is copied, else it is downloaded up to 100 MB more, while the server still gives it. Files saved earlier stay. | 1.0.9 |
| Archive counters | Under Download in advance: files saved in advance and when deleted, not downloaded, link expired (the server no longer gives the file of a deleted message) and deleted messages this device had no copy of, with Reset counters; the push diagnostics show the last number too. | 1.0.9 |
| Media without limits | Files of kept messages are copied to Downloads/MZGram/Saved Attachments, of any size and with no total limit, so they stay after the sender removes them. | 1.0.0 |
| Marks for deleted and edited messages | A mark before the time of a deleted or edited message (any text, or none); kept deleted messages are drawn at 75% opacity. | 1.0.0 |
| Export, import and wipe | Export the archive database to a file, import it back (replacing the current archive), or delete the whole archive and its saved media after a confirmation. From 1.0.9 the Delete button shows progress while the files go, then a message at the bottom of the screen says the archive is cleared and how much was freed. | 1.0.0 |
| Export with Save as | Export opens Android's Save as, so the archive file goes where you choose (Downloads, a file manager, a cloud drive), and says when it is saved. Import takes only an MZGram archive file and keeps the current archive otherwise. Up to 1.0.4 Export closed the app, so the archive of 1.0.3 and older cannot be moved. | 1.0.5 |
| Clear the local database | Clears Telegram's cache on the device (messages, chat list); it loads again from the server. The MZGram archive is kept. | 1.0.0 |
| Archive rules per chat | Settings > MZGram > Archive > Archive rules per chat adds Archive in this chat to a chat's menu: follow Keep messages from, always keep other people's deleted and edited messages there, or never keep them there. Off by default. | Next release |
| Replies to deleted messages | A reply whose original was deleted shows the copy kept in the archive instead of "Deleted message". A second switch sends your reply to a kept deleted message with a quote of it, since the server has no original to reply to. Off by default. | Next release |
| Forward deleted messages as copies | A kept deleted message has no original on the server, so Telegram cannot forward it; it is sent as a new message with its text and the file on this device, or only the text when the file is gone. Off by default. | Next release |
| Deleted messages in the chat menu | Adds Deleted messages to a chat's menu: the messages other people deleted there that the archive keeps, newest first, with search. A tap goes to the message, a long press copies its text. Off by default. | Next release |
| Deleted chats | A chat that leaves the chat list (deleted by either side, or left) or loses all its messages while the archive keeps some of them is listed in Settings > MZGram > Archive > Deleted chats; Telegram deletes the chat as usual. A second switch keeps such chats in the main chat list with their last kept message. Off by default. | Next release |
| Import from another client | Adds deleted and edited messages, deleted chats and last seen times from another client's message database (an SQLite file). What the archive has stays, and your own messages are left out. | Next release |

### Privacy and ghost mode

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Ghost mode | Other people no longer see that you read their messages or that you are typing. A message is marked read only when you reply or react. Telegram always sends both. | 1.0.0 |
| Delayed sending | While ghost mode is on, an outgoing message waits about 12 seconds (longer for media) before it is sent, so writing and sending at once does not show you online. | 1.0.0 |
| Send without sound | While ghost mode is on, every outgoing message is sent without a notification sound. | 1.0.0 |
| Ghost mode before stories | Before you open a story, MZGram offers to turn ghost mode on, so the story is not marked as seen. | 1.0.0 |
| Read time | Message details show when the other person read your message. | 1.0.0 |
| Approximate last seen | For a person who hides their last seen, MZGram shows an estimate from what this device saw (for example, their new messages), like any last seen time; up to 1.0.3 it was marked "(approx.)". | 1.0.0 |
| Protected content | In chats and channels that restrict saving, you can forward, save and copy messages and media and take screenshots. Messages that cannot be forwarded are sent as new copies. Off by default. | 1.0.0 |
| Ghost mode per chat | Adds Ghost mode in this chat to a chat's menu: follow the main switch, always show reads and typing there, or never show them there even while ghost mode is off. Delayed and silent sending follow the same choice. Off by default. | Next release |
| Quick settings tiles and shortcuts | Two tiles for the phone's quick settings and two shortcuts on a long press of the app icon: Ghost mode (turns it on or off) and Log in by QR (opens the scanner to log in on another device). Other apps cannot start them. | Next release |
| Confirm Telegram links | Settings > MZGram > Privacy. t.me and tg:// links show their address and ask before they open, so a hidden link cannot join a channel, start a bot or add a proxy in one tap. Off by default. | Next release |
| No link preview while typing | A link you type or paste is not sent to Telegram for a preview, so the server does not open it before you send; such messages go without a preview. Previews in received messages still show. Off by default. | Next release |
| Drafts on this device only | Text you typed but did not send is not uploaded to Telegram and does not show on your other devices. Off by default. | Next release |
| Voice messages on the lock screen | While the phone is locked, voice messages and round videos do not start or resume from the lock screen player or headset buttons, and their player hides the sender's name and photo. Music plays as usual. Off by default. | Next release |
| Own last seen | Telegram does not show you your own last seen. This device notes when it last told Telegram you are online or offline and shows it, for each account. Off by default. | Next release |
| Exact last seen times | While a person shows online or an exact last seen time, this device notes it, so after they hide their last seen MZGram still shows the last time it knew. Off by default: then only signs of activity (messages, typing) are noted, as before. | Next release |
| Readers in groups | Telegram lists who read your message only in small groups and only for about a week. Each time the message menu shows that list, it is kept on this device, and Details shows the readers and times later too. Off by default. | Next release |
| Ask before starting bots | A link like t.me/bot?start=code tells the bot which link brought you. MZGram asks first: start with the code, without it, or from another of your accounts. Off by default. | Next release |
| Reduce network tracking | Files are asked from Telegram's main servers instead of its CDN servers, so the downloads do not all carry the same key id that someone watching the network could use to link them. Off by default. | Next release |
| Turn off the proxy automatically | The proxy turns off by itself on the networks you pick (while a VPN is on, on mobile data, on Wi-Fi) and back on when the phone leaves them. Off by default. | Next release |
| Account passcodes | Each account can have its own passcode for the app lock: entering it unlocks the app and opens that account. An account with its own passcode can be hidden from the account lists and notifications. | Next release |
| Trending stickers not marked viewed | The server is not told which trending sticker and emoji packs you looked at; they are marked viewed on this device only. Off by default. | Next release |
| OpenFreeMap map preview | Map previews of locations in chats are loaded from staticmap.openstreetmap.de (OpenStreetMap) instead of Google Maps; the app asks that service directly. Off by default. | Next release |
| Tor companion proxy | Settings > MZGram > Other > Extended peripherals. Messages and media go through the SOCKS5 proxy of a Tor app (Orbot, 127.0.0.1:9050) from the first connection after every start; while the Tor app is not running, MZGram does not connect. Calls, web pages, mini apps and MZGram extras that use other services connect directly, not through Tor. Off by default. | Next release |

### Message menu

Each item has its own switch.

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Copy Photo | Copies a downloaded photo, to paste it into other apps. | 1.0.0 |
| Delete Downloaded File | Removes the downloaded file of a message from the device; the message stays and the file can be downloaded again. | 1.0.0 |
| Save Message | Forwards a message to Saved Messages in one tap. | 1.0.0 |
| Set a reminder | Schedules a copy of the message in Saved Messages at the time you pick, which notifies you then. | 1.0.0 |
| Repeat | Sends the same message into the chat again (or its text or sticker where forwarding is not allowed). | 1.0.0 |
| Open in... | Opens a video in another app, streaming it if it is not downloaded yet (Android 8 and newer). | 1.0.0 |
| Details | Ids, sender, dates, forward source, file, media and data center of a message; a long press copies a value. | 1.0.0 |
| QR Code | When a photo holds a QR code, shows its link or text. The photo is scanned on the device. | 1.0.0 |
| Forward without sender | Opens the forward screen with the sender's name already hidden. | 1.0.0 |
| Copy without formatting | Only the plain text of a message, without bold, links or emoji styling. Its switch is in Settings > MZGram > Chats. Off by default. | Next release |

### Ask AI

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Ask AI | Ask AI in the menu of a message with text, photos or files opens a box with the message, a prompt of your own and the site: Gemini, Claude, ChatGPT, DeepSeek or Grok (the default one is set in Settings > MZGram > AI). Open shows that site on an MZGram screen of its own, in Android System WebView with a profile of its own, so its sign-in and data stay apart from Mini Apps and the rest of Telegram; a WebView too old for profiles does not open the site and says so. No paid API: you sign in to the site with your own account. A script puts the prompt and the message into the site's input field; nothing is sent until you press Send on the site, and the site gets no access to the app. No site has an address parameter that only fills the field (ChatGPT's and Grok's send at once), so none is used. The model is chosen on the site: MZGram cannot pick it reliably there. Not in chats that restrict copying, unless Forward and save protected content is on. A link to another site opens in the browser. When a site changes its page, the screen says no field was found, and Copy text still works. Sign out of AI sites clears the profile. | 1.0.9 |
| Ask AI with photos and files | From 1.0.9 Ask AI also takes several selected messages (their texts with the senders' names) and the photos and files of the messages: up to 10 in all, each up to 30 MB. Pictures sent as files count as photos; documents such as txt, md, csv, json, xml, log, code, pdf, doc/docx, xls/xlsx, ppt/pptx, rtf and odt/ods/odp are taken, archives (zip, rar, 7z, tar, gz, apk and others), video, GIFs, stickers, voice and music are not. The box lists the attachments and what was left out and why. MZGram downloads what is not on the device yet; nothing is put into the site's page by itself. The site's own button for adding files opens MZGram's chooser, titled with the site: each file with its thumbnail or icon, its name, size and a check; Add gives the site every checked file at once, another file can still be chosen on the device, and a field that takes one file gets them one at each press of the button. The one-time warning says how files are added. A site uploads attachments to its servers as soon as they are added, before Send. When a site takes none, Save files saves them to Downloads/MZGram AI to add by hand. The warning that the text and attachments leave Telegram is shown only the first time; the box has no permanent note. | 1.0.9 |
| AI site buttons | Settings > MZGram > AI > Buttons in the chat list menu: a switch for each of the five sites puts a button into the three-dots menu of the chat list that opens that site on MZGram's AI screen without a message (no warning, as nothing leaves Telegram). Off by default. | 1.0.9 |

### Chats and pinning

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Pin in MZGram only | When the server allows no more pinned chats, more chats can be pinned on this device only, up to 10 in all: the server's pins first, then MZGram's, marked with their own pin. Nothing is sent to the server, and Premium is not needed or changed. Telegram stops at the server's limit. | 1.0.1 |
| Open archive on pull | Pulling the chat list down past the archive row opens the archive. | 1.0.0 |
| Media preview | A long press on a chat's avatar shows its last photo or video instead of the chat preview. | 1.0.0 |
| Delete for everyone by default | A switch: the Delete box of messages comes with deleting them for the other side too already ticked, and it can be unticked before deleting. Telegram leaves it unticked. | 1.0.8 |
| Up to 100 pinned chats | Past the server's limit, MZGram pins chats on this device: up to 100 in the chat list and in the archive instead of 10. The server and your other devices get only the chats the server allows. Off by default. | Next release |
| Local folders | Folders kept only on this device, without the server limits on folders and on chats in a folder. The folder editor gets a Local folder switch, and at the server limit a new folder becomes local instead of the Premium offer. They sync between your devices through a file (mzgram-folders.json) that MZGram keeps in Saved Messages. Off by default. | Next release |
| Folder on launch | The chosen folder opens when the app starts, separately for each account; Back from another folder returns to it first. Until a folder is chosen, the app opens as in Telegram. | Next release |
| Recent chats on Back | Holding the Back arrow in a chat lists the chats you opened last, to jump to one. The list is kept only on this device, without secret chats, and is erased when the option is turned off. Off by default. | Next release |
| Open profile by number | Settings > MZGram > Chats opens the profile of a user, group or channel by its number. A number the app has never seen is not found. | Next release |
| Search operators | In a chat's search: from:, date: (also today, yesterday), after:, before: and type: (photo, video, voice, round, music, gif, file, link, contact, location, poll, mention, pinned), together with plain words. Off by default. | Next release |
| Find names in fancy fonts | Search treats letters from decorated Unicode fonts (bold, script, circled, fullwidth and lookalike letters) as plain ones. Cyrillic, Greek, CJK and other real scripts are not changed. Off by default. | Next release |

### Messages in chats

Settings > MZGram > Chats.

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Stickers in chats | A slider for the size of stickers (100% as in Telegram), rounded corners, and hiding the time on stickers. | Next release |
| Wide channel posts | Channel posts take almost the whole width of the screen, with the channel avatar next to its name. Off by default. | Next release |
| Separate permissions | In group permissions and member restrictions, Stickers and GIFs is split into Stickers, GIFs, Games and Inline bots, which Telegram stores apart. A second switch makes the GIF tab and inline results in a group where you are restricted follow their own permission. Off by default. | Next release |
| Emoji links as custom emoji | A custom emoji that some apps send as a link over an ordinary emoji is shown as the custom emoji itself; the message keeps the link. Off by default. | Next release |
| Fit bold and italic links | Links and mentions in bold or italic are measured with their own font, so the text around them does not overlap or leave gaps. Off by default. | Next release |
| Code blocks without colours | Turns off syntax highlighting in code blocks. Off by default. | Next release |
| Colour preview for hex hashtags | A small square of the colour after hashtags like #FF5722. Off by default. | Next release |
| Default delay in the schedule dialog | The schedule dialog opens at now plus a delay set with a slider (5 minutes to 7 days) and has buttons for in 15 minutes, in 1 hour and tomorrow morning. A date picked earlier is not replaced. Off by default. | Next release |
| Send without sound by default | New messages are sent without a notification sound; scheduled messages and Saved Messages are not affected. Off by default. | Next release |
| Pinned reactions | Your own reactions that go first in the reaction panel above a message, separately for chats and channels. | Next release |
| Formatting menu | Which styles the menu shows when you select text in the message field, in the order you drag them to; Code block and Mention can be added to it. Until you change it the menu is as in Telegram. | Next release |
| Space between CJK and Latin | While typing, adds a space between Chinese, Japanese or Korean characters and Latin letters or digits. Off by default. | Next release |
| Character counter | Shows how many characters are typed, counted as the Bot API counts them. Off by default. | Next release |
| Inline calculator | An expression typed with "=" in the message field (25*40=) gets its result added (25*40=1000). It works offline, with + - * / ^ % and brackets. Off by default. | Next release |
| Double tap actions | What a double tap on a message does, separately for your own messages and other people's: Telegram's quick reaction, reply, copy the text, or nothing. Off by default. | Next release |
| Clean tracking from links | Tracking parameters (utm_source, fbclid, gclid, si and others) are removed from a link when you open it and when you paste it into a message. The rules are the AdGuard URL Tracking filter built into the app; it is never downloaded. Off by default. | Next release |

### Media and calls

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Ask before calling | Asks for confirmation before a voice or video call starts. | 1.0.0 |
| Confirm voice and video messages | After recording, a voice or round video message is shown for review with a Send button instead of being sent at once. | 1.0.0 |
| Instant camera off | The camera tile in the attach menu shows no live preview; the camera starts only on a tap. | 1.0.0 |
| Hide camera tile | Removes the camera tile, so the attach gallery starts with your first photo. | 1.0.0 |
| Original quality | Videos offered in several qualities play in the original one. | 1.0.0 |
| Auto-pause video | A video in the media viewer pauses when the app goes to the background and resumes when you return. | 1.0.0 |
| Always send photos in HD | The HD switch for photos you send is on from the start; it can still be turned off for a single photo. Off by default. | Next release |
| Subtitles in video player | A video in the media viewer can get a subtitle file (.srt or .vtt) from the file picker, and the choice stays with that video. Off by default. | Next release |
| Noise suppression for voice messages | While recording a voice message, turns on the phone's own noise suppressor and automatic gain control, if it has them. Off by default. | Next release |
| Voice message quality | Bitrate of the voice messages you record: a higher one sounds clearer and makes the file two or four times bigger. | Next release |
| Crop photos at full resolution | A crop is cut out of the original photo, not out of a copy already reduced to the send size, so a small crop stays sharp. Off by default. | Next release |
| Round video recording | Stabilization while recording a round video, if the camera has it; recording through Jetpack CameraX (switching cameras without a pause where the phone allows it, a zoom slider with the phone's lenses); or another Camera2 recorder with 60 FPS where both cameras allow it and its own bitrate, Telegram's by default. Each is off by default. | Next release |
| Pause round video on leaving the chat | A round video message stops playing when you switch to another chat or screen. Off by default. | Next release |
| Material 3 music player | Music opens in a full screen player with the cover colours, a wavy progress bar, playback speed, a queue and the lyrics from the file; voice messages keep Telegram's player. Off by default. | Next release |
| Profile music card | The saved or playing music of a user shows as a card in their profile. Off by default. | Next release |

### Interface and appearance

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| MZGram settings screen | Settings > MZGram opens with the app's icon, name and version with its build number, then the categories: General, Appearance, Chats, Archive, Ghost mode, Ads and filters, Privacy, AI, Plugins (only in a build with plugins) and Other, and a link to the source code on GitHub. On each page the switches are grouped in cards under headers, each with what it does in grey under its title; the message menu is a page of Chats. | Next release |
| Folder tabs at the bottom | Chat folder tabs sit at the bottom of the chat list, within reach of the thumb. | 1.0.0 |
| Sender mini avatars | Settings > MZGram > Appearance > Chat list. In groups, the line of the last message in the chat list starts with a small avatar of its sender (not for your own messages). Off by default. | Next release |
| Force snow | Settings > MZGram > Appearance > Chat list. Telegram's winter snowflakes fall over the chat list bar and chat backgrounds all year, not only in the holiday season. Off by default. | Next release |
| Chat list title and buttons | Settings > MZGram > Appearance > Chat list: Hide status (no emoji status or Premium star next to the chat list title), Center title (titles of the app's bars in the middle, as wide as the narrower side allows), Hide floating button (no new message and story buttons over the chat list; new chats start from Contacts), Hide search panel (no search field over the idle chat list; the search button of the bar opens search). Each off by default. | Next release |
| In-app vibration | Settings > MZGram > General. Off: buttons, menus, sliders, pickers and mini apps of the app, its sheets, dialogs and photo viewer no longer vibrate; calls still ring and vibrate, notifications follow their own settings. On by default, as in Telegram. | Next release |
| Disable notification delay | Settings > MZGram > General. A new message notifies at once; Telegram waits 1 second, or 3 while the account is online on another device, in case it is read there. Off by default. | Next release |
| Download and upload boost | Settings > MZGram > General > Download boost: Off (Telegram's parts), Fast (512 KB parts, 8 at once, as Telegram's experimental mode) or Ultra (1 MB parts, 12 at once; can make downloads and video streaming worse on a slow connection). Faster uploading sends files in parts of 512 KB instead of 128 KB. Both off by default. | Next release |
| Relative last seen | Settings > MZGram > General. Within the last day, last seen reads "last seen 5 minutes ago" or "last seen 3 hours ago" instead of "last seen today at 13:51", in Telegram's own words and language; the chat header refreshes it every minute. Off by default. | Next release |
| Hide phone number | Settings > MZGram > General. The account's own number is not shown in the header of Telegram's Settings, in the own profile and in the Phone number row (Mobile hidden), so it stays out of screenshots and screen shares. Others see it as before. Off by default. | Next release |
| Hide archive from the chat list | Settings > MZGram > General > Chat archive. The Archived chats row is gone from the chat list (also the hidden one you pull down), and the three-dots menu of the chat list gets Archived chats. Chats stay archived. Off by default. | Next release |
| Disable "Unarchive" swipe | Settings > MZGram > General > Chat archive. A swipe in the archive no longer takes the chat out of it. Off by default. | Next release |
| Save folder | Settings > MZGram > General > Save folder: the name of the folder that Save to gallery, Save to downloads and Save to music use inside Pictures, Movies, Download and Music: Telegram (the default, as in Telegram), MZGram or a name of one's own. Files saved before stay where they are. | Next release |
| No bottom navigation bar | Removes the tab bar under the chat list; its pages move to the chat list menu. | 1.0.0 |
| Accounts in the chat list menu | The menu of the three dots at the top of the chat list ends with every signed-in account (avatar and name, the current one marked, the unread count of the others); a tap switches to it at once, without opening Settings. Add Account is there while there is room for one (more with Premium). The standard items stay. On by default, can be switched off. | 1.0.3 |
| Hide stories | Removes the stories row from the chat list. | 1.0.0 |
| Seconds in message time | Message times show seconds, such as 12:34:56. | 1.0.0 |
| Exact numbers | Views, reactions and members show exact counts (4777 instead of 4.8K). | 1.0.0 |
| No greeting sticker | An empty chat shows no suggested sticker. | 1.0.0 |
| Hide channel bottom button | In channels where you cannot post, hides the bottom bar with the mute button. | 1.0.0 |
| Animation switches | Switches for the predictive back animation and the gooey avatar animation in profiles. | 1.0.0 |
| MZGram first in Settings | The MZGram row ("Features of this client") is the first row of Telegram's settings list, in the same group and with the same look as My Account and the other settings, right above My Account (next release; in 1.0.5 it was a group of its own above the accounts). "Features of this client" is only that row's subtitle, not a text on the MZGram page. | 1.0.5 |
| 12 languages | The MZGram interface is available in 12 languages: English, Ukrainian, Russian, Polish, Spanish, German, French, Portuguese (Brazil), Turkish, Arabic, Belarusian and Kazakh. Every MZGram switch, setting, menu item, dialog, error, the update banner and the push diagnostics are complete in each; for any other language they are in English, never a mix. From the next release every MZGram text is in the chosen MZGram language, never in Telegram's: the buttons of MZGram's dialogs (Cancel, OK, Save, Delete), the update banner and the install permission dialog (also right after the language is changed), the MZGram items of the message menu, the QR code and message details screens and their dates. English and Ukrainian since 1.0.2. | 1.0.4 |
| MZGram language | Settings > MZGram > General > MZGram language: Same as Telegram (the default from 1.0.9: Telegram's language when MZGram has it, otherwise English), System (the phone's language when MZGram has it, otherwise English) or one of the 12. Up to 1.0.8 the default was System, so MZGram's texts could be in another language than Telegram's; a language chosen before is kept. Before 1.0.4, some MZGram texts could switch to the phone's language. | 1.0.4 |
| Show ID | Settings > MZGram > General > Show ID: the ID of a user, bot, group or channel as a row of its profile: Hidden (the default), as in Telegram API, or as in Bot API (-100 before a channel or supergroup, - before a group). A tap copies it. | 1.0.9 |
| Own ID | With Show ID on, the own ID is also a line of the own header of Settings, below the number and the username, in the same form; a tap copies it. | 1.0.9 |
| Tall names in Settings | The number and the username in the own header of Settings stay below the name also when the name is taller than a line of the usual font, as a name with runes. | 1.0.9 |
| Hide rows of Telegram's Settings | Settings > MZGram > Other > Hide in Telegram's Settings: a switch for each of Telegram Premium, Telegram Stars, Gram Earnings, Telegram Business, Send a Gift, Wallet, Ask a Question, Telegram FAQ, Telegram Features and Privacy Policy. A hidden row is gone from Telegram's Settings and from its search; nothing else changes. All off by default. | 1.0.9 |
| Standard color themes | The color theme row in Appearance always lists the home theme and 8 standard themes built on the device, also without a server answer and after an update; themes from the server are added after them. The theme in use stays selected after Switch to Night Mode. | 1.0.1 |
| App font choices | Settings > MZGram > Appearance > App font, for names, titles and bold and italic text: Telegram's font (the default), the phone's font, the built-in Inter or Fira Sans (OFL 1.1), or a TTF or OTF file, which also changes the regular text where Android allows it. Takes effect after a restart. | Next release |
| Compact chat header | A smaller avatar and less spacing in the title bar of a chat. Off by default. | Next release |
| Material 3 switches and cards | Settings > MZGram > Appearance: Material 3 switches across the app, and list items grouped into rounded cards. Each is off by default. | Next release |
| Persian (Solar Hijri) calendar | Dates in the chat list, date headers in chats, the call log and last seen follow the Solar Hijri calendar, with Persian month names and digits. Off by default. | Next release |
| Approximate registration date | In a user profile, next to the ID, the approximate month and year the account was made, estimated from the ID with a table in the app, so it can be off by a few months. Needs Show ID. Off by default. | Next release |
| Hide status in chat header | Hides the line under the chat name (last seen, online, members or subscribers); "typing" stays. Off by default. | Next release |
| Static input field icons | The microphone, camera and emoji buttons of the message field change without animation. Off by default. | Next release |
| New chat button shape | The shape of the round button of the chat list. | Next release |
| Folder tabs | What the folder tabs above the chat list show. The icon is the one set for the folder in Telegram Desktop or on iOS; without it, one is picked from the chat types of the folder. | Next release |
| Account order | Your accounts in the order you choose; Settings, the account switcher and the accounts in the menu follow it. | Next release |
| Side menu | The three dots of the chat list open a panel from the side: your avatar and name, the accounts and the items you choose, with options for a QR button, a large avatar, the header background and a glass panel. Full menu in it opens the usual menu. Off by default. | Next release |
| Pills in the search bar | Small pills at the end of the search bar of the chat list, one at a time: ghost mode, last seen, proxy, cache, CPU, RAM, network speed, data center ping, weather and exchange rates. The weather pill uses Telegram's own weather (with the location permission); the rates pill asks Coinbase and gold-api.com directly. Off by default, with no pill chosen. | Next release |
| Icon packs | Other icons in place of Telegram's own: a built-in set or packs from .icons files. MZGram does not download packs. Off by default. | Next release |
| Monet themes | Three themes in the colours of your wallpaper (Material You), light, dark and black, beside the standard ones; they follow a new wallpaper. Off by default. | Next release |
| Don't load hidden stories | With Hide stories on, the stories row and story previews are not loaded in the background, which saves traffic and battery. Off by default. | Next release |
| Avatar shape | A slider for how round avatars are across the app, from a circle as in Telegram through a rounded square to a square. | Next release |
| Glass outline | The edge of glass panels, buttons and menus: glare as in Telegram, one thin solid line, or none. | Next release |

### Ads and filters

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| No sponsored messages | Sponsored messages in channels and the promo or proxy banner in the chat list are neither requested nor shown. | 1.0.0 |
| Zalgo filter | Removes stacked Unicode combining marks (corrupted "Zalgo" text) from names, chat titles and message text. | 1.0.0 |
| Reaction filter | Hides all reactions or chosen emoji under messages, separately in private chats, groups and channels. Only what you see changes. Off by default. | Next release |
| Shadow ban | In groups, messages of the people you choose and replies to them are not shown and do not notify; they are not told. A second switch hides messages of blocked users in groups. Off by default. | Next release |
| Message filters | Messages from other people that match your rules (regular expressions) are not shown and do not notify. Nothing is sent anywhere. Off by default. | Next release |

### Notifications

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| UnifiedPush | Notifications come through a UnifiedPush distributor: the built-in Google FCM, or an app such as ntfy, microG or Sunup. Telegram's own Google push does not reach unofficial builds. | 1.0.0 |
| Push gateway | A gateway the user can change; it cannot read notifications (the keys stay on the device). | 1.0.0 |
| Push diagnostics and test push | The UnifiedPush page shows what happened to the last notifications and can send a test push through the whole path. | 1.0.0 |
| Device check | Notifications and Sounds > UnifiedPush > Device check: whether notifications are allowed, calls pop up on screen, the battery limits the app, Reliable delivery is on and, on MIUI, its autostart, pop-ups in the background and lock screen permissions. Each row opens the system screen; MZGram changes nothing itself. When the phone limits the app in the background, it suggests Reliable delivery. Share push log gives a log in English without the push token or keys, with Reliable delivery's journal. | 1.0.9 |
| Reliable delivery | Notifications and Sounds > Reliable delivery: a permanent quiet notification keeps the app and its connection running, so that messages and calls come on time on phones that stop apps in the background. Unlike Telegram's Keep-Alive Service it runs in the foreground; it starts again after a reboot or an update and is checked every 15 minutes. One switch for all accounts, off by default; the notification has no buttons, and a tap opens the switch. | 1.0.9 |

### Storage

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| 1 GB cache limit | Maximum cache size can be set to 1 GB (Telegram starts at 5 GB); a lower limit clears the cache at once. | 1.0.1 |
| Settings backup | Settings > MZGram > Other: export the MZGram settings to a file you choose and import them back. API keys, push keys, plugins, passcode accounts and the app font are not in it. Nothing is sent anywhere. | Next release |
| Diagnostics | Settings > MZGram > Other > Diagnostics: a crash report shown once on the next launch after a crash, and a frame drop profiler and a battery profiler that write text reports you may share. Nothing is sent by MZGram. | Next release |

### Updates and branding

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| MZGram update banner | Every few hours MZGram checks its latest stable release on GitHub and shows a banner when a newer one is out; Close hides that version. Telegram's own update check is turned off. Can be switched off. | 1.0.1 |
| Self-update | Update in the banner downloads the APK of that release (for the device's architecture, else the universal one) only from the MZGram-Android releases on GitHub over HTTPS, checks its SHA-256 against the release's SHA256SUMS and GitHub's digest, and installs it with Android's package installer; Android may ask to confirm, and the first time asks to allow installing apps from MZGram. Progress is shown in the banner; a broken download resumes; a file that fails the check is deleted and nothing is installed. On an error: Retry or the release page. When a release has no APK that can be checked, the release page opens. Only an APK of MZGram's own package is installed. The switch of the update banner turns off the automatic check; from the next release Update also works in a banner shown by Check for updates with that switch off (before, it did nothing then). The version before it is installed by hand once. | 1.0.2 |
| Check for updates | Settings > MZGram > Other > Check for updates looks at once, outside the interval and also with the banner switched off, and says what it found: the latest version is installed, a newer one is available (it also gets the banner), or the check failed (no network, a GitHub error). | 1.0.4 |
| MZGram name | The app calls itself MZGram in dialogs, permission requests, call notifications, contacts and in the version line of Settings ("MZGram 1.0.2 (based on Telegram 12.10.1)"), in every language. From 1.0.9 the whole version line, with its build and the kind of APK, is in the MZGram language; before, part of it stayed in English. The Telegram service keeps its name. | 1.0.2 |
| Own package | MZGram is org.mzgram.messenger, a separate app beside Telegram from Google Play and from telegram.org (up to 1.0.3 MZGram had the package of Telegram from telegram.org, so Android took it for that app's update and did not install it). Its content providers, permissions and contacts account are its own, and its launcher icon is violet, not Telegram's blue. tg:// and t.me links may ask which app opens them. MZGram 1.0.3 and older do not update to it: install it by hand once and sign in. The archive of the old MZGram is not moved (Export did not work in 1.0.3), so the new MZGram starts with an empty archive; files of archived messages stay in Downloads/MZGram/Saved Attachments. | 1.0.4 |

### Plugins and hooks

Only in builds made with -PmzgramPlugins=true; the usual build has no plugins.

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Plugins | Settings > MZGram > Plugins: single-file Python plugins (.plugin, CPython 3.11). Before a plugin is added, a sheet shows the permissions it asks for and what an offline scan of its code found; its code can be read, an activity log keeps what plugins did, and plugins that crash the app at start are stopped by Safe Mode. A plugin runs with the rights of MZGram: the checks on network, files, processes and sending are not a sandbox, so turn on only plugins you trust. | Next release |
| Plugin hooks and Aliuhook | With the hooks permission a plugin can hook Java methods of the app through Aliuhook (com.aliucord:Aliuhook:1.1.4, OSL-3.0), which wraps LSPlant (LGPL-3.0). Such a plugin has full control over the app, also over the checks above. | Next release |
| Python packages and plugin development | A switch allows installing pure-Python packages from PyPI for a plugin when it asks; off by default. Another switch lets you add and update plugins from a computer over adb, each command with a token. | Next release |
| Plugins catalog | Opens a catalog (index.json) from a URL and installs a plugin only when the SHA-256 of its file matches the catalog. The default URL is the MZGram-Plugins repository, which is not published yet. The catalog loads only when its screen is opened. | Next release |
| Plugins backup and restore | Plugins and their settings, without keys, tokens and passwords, go into the settings backup (Include plugins in settings backup, off by default) or a .plugins.zip file. Restored plugins come back turned off and without permissions. | Next release |

### Releases

| Feature | What it does | Since |
|---|---|---|
| Signed releases | Every release APK is signed with the MZGram key, with a key rotation from the earlier key so it installs over earlier MZGram builds and keeps their data (up to 1.0.3; the next release has its own package and is installed beside them). `SHA256SUMS` and the signing certificate are published with each release. | 1.0.0 |
| Telegram for Android base | Based on Telegram for Android 13.1.1 (from 1.0.10; 13.0.3 in 1.0.7 to 1.0.9). It needs Android 6.0 or newer: Telegram 13.0 dropped Android 5. | 1.0.7 |

## Desktop

### Message archive

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Deleted messages are kept | In every chat, another person's message stays after they delete it, from a local copy. Telegram removes it. Your own messages are never kept. On by default. | 1.0.0 |
| Edit history | Every earlier version of another person's edited message is kept and shown in an edit history window opened from the message. | 1.0.0 |
| View-once media kept | View-once media of other people is kept with its file. | 1.0.0 |
| Chats with only deleted messages stay | When the other side deletes the only message of a chat or its whole history, the chat stays in the chat list with the newest kept message while the app runs. After a restart the chat list comes from the server, which no longer lists such a chat; the kept messages are there again when the chat is opened. | 1.0.2 |
| Archive switches | Separate switches for deleted messages, edit history, media files, formatting and reactions. | 1.0.0 |
| Keep messages from | Four switches, all on by default: from users, from bots, in groups, in channels. A bot's message follows From bots in any chat; other messages follow the kind of chat, and a channel writing in a group or an anonymous admin counts as the group. Turning one off stops new saves; what is kept stays. | 1.0.9 |
| Archive size | The Archive topic starts with the archive's size as Telegram's Storage Usage shows it: the total, a bar split by kind and a legend with the messages database, the media, Saved Attachments and other files. It is counted in the background and shows 0 right after Clear archive, which also gives the database's space back. | 1.0.9 |
| Download in advance | Settings > MZGram > Archive > Download in advance. Every message from someone else is kept as it comes, with what is needed to download its file later. Its file is downloaded at once up to a limit for each kind of chat: users 300 MB, bots 5 MB, groups 20 MB, channels 5 MB, or No limit; before, every file was downloaded whatever its size. When the message is deleted, a file already on the computer is copied, else it is downloaded up to 100 MB more, while the server still gives it. Files saved earlier stay. | 1.0.9 |
| Archive counters | Under Download in advance: files saved in advance and when deleted, not downloaded and link expired (the server no longer gives the file of a deleted message), with Reset counters. | 1.0.9 |
| Media without limits | Files of kept messages are saved in a Saved Attachments folder, of any size and with no total limit. | 1.0.0 |
| Marks for deleted and edited messages | A mark before the time of a deleted or edited message (any text, or none); kept deleted messages are dimmed. | 1.0.0 |
| Export, import and wipe | Export the archive database, import it back, or delete the whole archive after a confirmation. From 1.0.9 the box closes at once, the files are deleted in the background and a message says the archive is cleared and how much was freed. | 1.0.0 |
| Clear Telegram's local cache | Clears the local cache of Telegram on this computer and restarts; the MZGram archive is kept. | 1.0.0 |

### Privacy and ghost mode

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Ghost mode | Hides your read receipts, typing and online status; a switch for each lets it through. A message is marked read only when you reply or react. | 1.0.0 |
| Delayed sending | While ghost mode is on, an outgoing message waits before it is sent, so writing and sending at once does not show you online. | 1.0.0 |
| Send without sound | While ghost mode is on, every outgoing message is sent without a notification sound. | 1.0.0 |
| Ghost mode before stories | Before you open a story, MZGram offers to turn ghost mode on. | 1.0.0 |
| Read time | Message details show when the other person read your message. | 1.0.0 |
| Approximate last seen | For a person who hides their last seen, MZGram shows an estimate from what this computer saw, like any last seen time; up to 1.0.1 it was marked "(approx.)". | 1.0.0 |
| Protected content | In chats and channels that restrict saving, you can forward, save and copy their messages and media; they are sent as new messages without the original sender. Off by default. | 1.0.0 |

### Message menu

Each item has its own switch.

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Save Message | Forwards a message to Saved Messages in one click. | 1.0.0 |
| Set a reminder | Schedules a copy of the message in Saved Messages at the time you pick. | 1.0.0 |
| Repeat | Sends the same message into the chat again. | 1.0.0 |
| Open in... | Opens a downloaded file in another program. | 1.0.0 |
| Message details | Ids, dates, views, file and media of a message. | 1.0.0 |
| Scan QR code | When a downloaded photo holds a QR code, shows its link or text. Scanned on the computer. | 1.0.0 |
| Copy Photo | Copies a photo, or a downloaded picture sent as a file, to paste it into other programs; while it is on, it stands for Telegram Desktop's Copy Image. | 1.0.9 |
| Delete Downloaded File | Removes the downloaded file of a message from this computer after a confirmation that names the file; the message stays and the file can be downloaded again. | 1.0.9 |
| Forward without sender | Opens the forward box with the sender's name already hidden. | 1.0.9 |
| In every chat | From 1.0.9 the MZGram items are in the menu of a message in the main chat too; before, only in threads, topics and the other lists opened from a chat. | 1.0.9 |

### Ask AI

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Ask AI | Ask AI in the menu of a message with text, photos or files opens a box with the message, a prompt of your own and the site: Gemini, Claude, ChatGPT, DeepSeek or Grok (the default one is set in Settings > MZGram > AI). Open shows that site in an MZGram window of its own, in the same built-in browser as Mini Apps (WebView2 on Windows, WebKitGTK on Linux), with a sign-in profile apart from Telegram's. No paid API: you sign in to the site with your own account. A script puts the prompt and the message into the site's input field; nothing is sent until you press Send on the site. No site has an address parameter that only fills the field (ChatGPT's and Grok's send at once), so none is used. The model is chosen on the site: MZGram cannot pick it reliably there. Not in chats that restrict copying, unless Forward and save protected content is on. When a site changes its page, the window says no field was found, and Copy text still works. Sign out of AI sites starts a new profile. | 1.0.9 |
| Ask AI with photos and files | From 1.0.9 Ask AI also takes several selected messages (their texts with the senders' names) and the photos and files of the messages: up to 10 in all, each up to 30 MB. Pictures sent as files count as photos; documents such as txt, md, csv, json, xml, log, code, pdf, doc/docx, xls/xlsx, ppt/pptx, rtf and odt/ods/odp are taken, archives (zip, rar, 7z, tar, gz, apk and others), video, GIFs, stickers, voice and music are not. The box lists the attachments and what was left out and why. MZGram downloads what is not on the computer yet; nothing is put into the site's page by itself. The site's own button for adding files opens MZGram's chooser, titled with the site: each file with its thumbnail or icon, its name, size and a check; Add gives the site every checked file at once, another file can still be chosen on the computer, and a field that takes one file gets them one at each press of the button. The one-time warning says how files are added. A site uploads attachments to its servers as soon as they are added, before Send. When a site takes none, Save files saves them to Downloads/MZGram AI to add by hand. The warning that the text and attachments leave Telegram is shown only the first time; the box has no permanent note. | 1.0.9 |
| AI site buttons | Settings > MZGram > AI > Buttons in the main menu: a switch for each of the five sites puts a button into the main menu that opens that site in MZGram's AI window without a message (no warning, as nothing leaves Telegram). Off by default. | 1.0.9 |
| Ask AI about selected messages | The selection bar has an Ask AI button next to Forward and Delete, and the menu of a message outside the selection offers Ask AI about the selected ones; any number of messages. | 1.0.9 |
| AI window layout | A site opened alone from the main menu has no empty bar above the page; the status wraps beside the buttons instead of going under them; the chooser lists another file on the computer as the last row of its list, with only Cancel and Add below. | 1.0.9 |

### Chats and pinning

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Pin in MZGram only | When the server allows no more pinned chats, more chats can be pinned on this computer only, up to 10 in all, after the server's pins and marked with their own pin. Nothing is sent to the server; Premium is not needed or changed. | 1.0.0 |
| Open archive on pull | Pulling the chat list down past the Archive row opens it. | 1.0.0 |
| Media preview | Holding a chat's avatar shows its last photo or video instead of the chat preview. | 1.0.0 |

### Media

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Confirm voice messages | Asks before a recorded voice message is sent. | 1.0.0 |
| Confirm round video messages | Asks before a recorded round video message is sent. | 1.0.0 |
| Auto-pause video | A video in the media viewer pauses when its window loses focus or is minimized, and resumes when you return. | 1.0.0 |

### Interface

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| Seconds in message time | Message times show seconds. | 1.0.0 |
| Hide stories | Removes stories from the chat list. | 1.0.0 |
| No greeting sticker | An empty chat shows no suggested sticker. | 1.0.0 |
| Hide channel bottom button | Hides the bottom button of channels where you cannot post. | 1.0.0 |
| 12 languages | The MZGram interface is available in 12 languages: English, Ukrainian, Russian, Polish, Spanish, German, French, Portuguese (Brazil), Turkish, Arabic, Belarusian and Kazakh. Every MZGram setting, menu item, dialog and the update bar are complete in each; for any other language they are in English, never a mix. From the next release every MZGram text is in the chosen MZGram language, never in Telegram's: the buttons of MZGram's boxes (Cancel, OK, Save, Delete, Close, Send), the dates on them, and the update bar, which also follows a change of the language at once. English and Ukrainian since 1.0.0. | 1.0.2 |
| MZGram language | Settings > MZGram > Interface > MZGram language: Same as Telegram (the default from 1.0.9: Telegram Desktop's language when MZGram has it, otherwise English), System (the system's language when MZGram has it, otherwise English) or one of the 12. Up to 1.0.8 the default was System, so MZGram's texts could be in another language than Telegram Desktop's; a language chosen before is kept. | 1.0.2 |
| Show ID | Settings > MZGram > Interface > Show ID: the ID of a user, bot, group or channel as a row of its profile: Hidden (the default), as in Telegram API, or as in Bot API (-100 before a channel or supergroup, - before a group). A click copies it. | 1.0.9 |
| Own ID | With Show ID on, the own ID is also a line of the own header of Settings, below the username, in the same form; a click copies it. | 1.0.9 |
| Runes in names | Characters of names that the usual font has no glyphs for, as Old Turkic runes, Gothic, Egyptian hieroglyphs or Glagolitic, come from Windows' own Segoe UI Historic instead of squares; other text keeps its font. | 1.0.9 |
| Hide rows of Telegram's Settings | Settings > MZGram > Interface > Hide in Telegram's Settings: a switch for each of Telegram Premium, My Stars, Gram Earnings, Telegram Business, Premium Gifting, Telegram FAQ, Telegram Features and Ask a Question (with the last three hidden, the whole help group goes). A hidden row is gone from Telegram's Settings and from its search; nothing else changes. All off by default. | 1.0.9 |
| Topics as on Android | Settings > MZGram lists its topics in Android's order: Archive, Ghost mode, Message menu, Media and calls, Interface, Ads and filters, AI, Other; Other holds Check for updates. The note under the topics is gone. | 1.0.9 |

### Ads and filters

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| No sponsored messages | Sponsored messages in channels and the promo banner in the chat list are not shown. | 1.0.0 |
| Zalgo filter | Removes stacked Unicode combining marks from names, chat titles and message text. | 1.0.0 |

### Updates and branding

| Feature | What it does, and how it differs from Telegram | Since |
|---|---|---|
| MZGram update bar | Every few hours MZGram checks its latest stable release on GitHub and shows a bar in the chat list when a newer one is out; Close hides that version. Telegram Desktop's own update checks are turned off. Can be switched off. | 1.0.0 |
| Self-update | Update in the bar downloads the archive of that release for this system and architecture (Windows x64 or ARM64 zip, Linux x64 or ARM64 tar.xz) only from the MZGram-Desktop releases on GitHub over HTTPS, checks its SHA-256 against the release's SHA256SUMS and GitHub's digest, unpacks it and restarts; the Updater copies the new files, backs up every file it replaces and puts them back if a copy fails, so the old version keeps working. The data folder is not touched. Progress is shown in the bar; a file that fails the check is deleted and nothing is installed; on an error: Retry or the release page. When the MZGram folder cannot be written, the bar says so and offers the release page. Turned off with the update bar. The version before it is installed by hand once. Up to 1.0.7 Update never got past GitHub's redirect to its download server and said the version could not be downloaded, whatever the connection; from 1.0.8 it follows that redirect, and MZGram 1.0.4 to 1.0.7 install 1.0.8 by hand once. From 1.0.8 the bar also says why an update stopped: no network, a GitHub error or its hourly limit, an address MZGram does not trust, a release that changed on GitHub, a file that could not be saved, the SHA-256 check or unpacking; log.txt gets the reason. | 1.0.1 |
| Check for updates | Settings > MZGram > Other > Check for updates (up to 1.0.8 in Interface) looks at once, outside the interval and also with the bar switched off, and says what it found: the latest version is installed, a newer one is available (it also gets the bar), or the check failed. | 1.0.2 |
| Own data folder | MZGram keeps its data apart from Telegram Desktop, so both can be installed. | 1.0.0 |
| Unblock MZGram files | On Windows, Settings > MZGram > Other > Unblock MZGram files removes the "downloaded from the internet" mark (Zone.Identifier) from the files in MZGram's folder, only when the button is pressed and after a warning, so Windows stops asking before they run. MZGram never turns off or changes Windows protection, SmartScreen or Defender, and adds no exclusions. When the folder needs administrator rights, it says to run Unblock-File in PowerShell as administrator. Files MZGram downloads itself when it updates carry no such mark. | 1.0.9 |
| Links beside Telegram Desktop | On Windows MZGram registers tg://, tonsite:// and ton:// links under its own name: Settings > Apps > Default apps lists MZGram Desktop apart from Telegram Desktop, and the app chosen there opens these links. MZGram does not take the links from Telegram Desktop or another app; it opens them by default only when no app has them, the app that had them is gone, or the `registertg` code asks. Up to 1.0.4 MZGram wrote itself into Telegram Desktop's registration, so Telegram Desktop's entry in Default apps was called MZGram Desktop and its links could open MZGram, also from a folder removed later. MZGram gives that registration back to Telegram Desktop, or removes it when Telegram Desktop is not installed; since the next release also the tg:// link 1.0.4 took from it, with Telegram Desktop found through its installer when needed. | 1.0.6 |
| Links beside Telegram Desktop on Linux | On Linux MZGram becomes the app for tg://, tonsite:// and ton:// links only when no app has them, the app that had them is gone, or the `registertg` code asks; otherwise it is added to the apps these links can be opened with, and Telegram Desktop or the app chosen before stays the default. Up to 1.0.6 MZGram made itself the default after every update. | 1.0.7 |
| MZGram name | Window titles, the About box ("MZGram Desktop X.Y.Z, an unofficial client based on Telegram Desktop", GPL, GitHub), menus, tray, notifications, the Windows app id and Start menu shortcut, and the exe properties say MZGram, in every language. The Telegram service keeps its name. | 1.0.1 |

### Releases

| Feature | What it does | Since |
|---|---|---|
| Windows x64 and ARM64 | A zip for each, with `MZGram.exe`. MZGram is built with Qt 6, so it needs Windows 10 version 1809 or newer; Telegram Desktop's own x64 build is made with Qt 5 for Windows 7. | 1.0.0 |
| Windows x64 without extra DLLs | `MZGram.exe` needs nothing but Windows. Up to 1.0.4 the x64 one also needed `libpq.dll` (PostgreSQL's client library, never used) and did not start on a computer without it; ARM64 and Linux were not affected. CI checks every DLL the executables import and starts the new build with Windows only. | 1.0.6 |
| Linux x64 | A tar.xz, built against glibc 2.28 so it runs on current distributions. | 1.0.0 |
| Linux ARM64 | A tar.xz for aarch64 machines such as Snapdragon laptops. | 1.0.1 |
| Source archive and checksums | Every release has the complete source (`-source.tar.xz`) and `SHA256SUMS`. | 1.0.0 |
| Telegram Desktop base | Based on Telegram Desktop 7.4.1 (from 1.0.10; 7.3.1 in 1.0.6 to 1.0.9), with its security fix of 7.2.9: a link that another launch of the app passes on to the running one (a tg:// link opened in a browser, for example) stays one link even with `;` in it, and local files that come together with a link are not opened or sent. Up to 1.0.4 (Telegram Desktop 7.2.8) such a link could make the running app send local files to a chat. | 1.0.6 |

## Android and Desktop compared

Yes: present. No: absent. Partly: present in a narrower form.

| Feature | Android | Desktop |
|---|---|---|
| Deleted messages, edit history, view-once media kept | Yes | Yes |
| Keep messages from users, bots, groups, channels | Yes | Yes |
| Archive size by kind | Yes | Yes |
| Download in advance by the kind of chat, archive counters | Yes | Yes |
| A chat with only deleted messages stays in the list | Yes | Yes (until a restart) |
| Archive export, import, wipe | Yes | Yes |
| Ghost mode: read receipts and typing | Yes | Yes |
| Ghost mode: online status | No | Yes |
| Ghost mode: delayed and silent sending, before stories | Yes | Yes |
| Read time, approximate last seen | Yes | Yes |
| Protected content | Yes | Yes |
| Message menu: Save, Reminder, Repeat, Open in, Details, QR | Yes | Yes |
| Message menu: Copy Photo, Delete Downloaded File, Forward without sender | Yes | Yes |
| Ask AI: a message in an AI site's chat in MZGram's own window | Yes | Yes |
| Ask AI: several messages, up to 10 photos and files | Yes | Yes |
| Ask AI: the site's own button for adding files opens MZGram's chooser | Yes | Yes |
| Buttons that open an AI site without a message | Yes (chat list menu) | Yes (main menu) |
| Ask AI button in the selection bar | No | Yes |
| Pin in MZGram only | Yes | Yes |
| Delete for everyone ticked by default | Yes (a switch) | Yes (Telegram Desktop ticks it unless told to remember otherwise) |
| Confirm voice and round video messages | Yes (one switch) | Yes (two switches) |
| Ask before calling | Yes | No |
| Instant camera and camera tile switches | Yes | No |
| Original video quality | Yes | No |
| Folder tabs at the bottom, no bottom bar | Yes | No |
| Accounts in the menu, one tap to switch | Yes | Yes (Telegram Desktop's side menu) |
| Exact numbers, animation switches | Yes | No |
| Seconds in time, hide stories, no greeting sticker, channel button | Yes | Yes |
| No sponsored messages, Zalgo filter | Yes | Yes |
| Standard color themes without the server | Yes | No (Telegram Desktop has them built in) |
| 1 GB cache limit | Yes | No (Telegram Desktop already offers it) |
| UnifiedPush notifications | Yes | No |
| Reliable delivery, Device check | Yes | No |
| Interface in 12 languages, complete in each, chosen apart from Telegram's | Yes | Yes |
| MZGram language Same as Telegram by default | Yes | Yes |
| Hide rows of Telegram's Settings | Yes | Yes |
| Show ID in profiles | Yes | Yes |
| Own ID in the header of Settings | Yes | Yes |
| Unblock MZGram files | No (Windows only) | Yes (Windows) |
| MZGram update banner, Telegram's updater off | Yes | Yes |
| Check for updates by hand, with the result | Yes | Yes |
| Self-update from GitHub with a SHA-256 check | Yes | Yes |
| MZGram name everywhere | Yes | Yes |
| ARM64 builds | Yes (the APK) | Yes |
| Archive rules per chat, deleted messages list, deleted chats | Yes | No |
| Replies to and forwards of kept deleted messages | Yes | No |
| Import from another client's database | Yes | No |
| Ghost mode per chat, quick settings tiles and shortcuts | Yes | No |
| Confirm links, no link preview, local drafts, lock screen voice, bot start | Yes | No |
| Own last seen, exact last seen, readers in groups | Yes | No |
| Reduce network tracking, proxy auto-off, trending stickers | Yes | No |
| Account passcodes | Yes | No |
| OpenStreetMap map preview | Yes | No |
| Tor companion proxy (messages and media) | Yes | No |
| Up to 100 pinned chats, local folders | Yes | No |
| Folder on launch, recent chats on Back, account order | Yes | No |
| Search operators, fancy fonts in search, open profile by number | Yes | No |
| Sticker size and shape, wide channel posts, link and code display | Yes | No |
| Separate sticker, GIF, game and inline permissions | Yes | No |
| Schedule delay, silent by default, pinned reactions, formatting menu | Yes | No |
| Character counter, inline calculator, CJK spacing, double tap actions | Yes | No |
| Clean tracking from links, copy without formatting | Yes | No |
| Voice messages: noise suppression and quality | Yes | No |
| Round videos: stabilization, CameraX, Camera2 recorder, pause | Yes | No |
| Photos: full resolution crop, always HD | Yes | No |
| Material 3 music player, profile music card | Yes | No |
| Video player subtitles (.srt, .vtt) | Yes | No |
| App font: Inter, Fira Sans or a file | Yes | No |
| Compact chat header, hide chat status, static input icons | Yes | No |
| Side menu, pills, folder tabs, new chat button shape | Yes | No |
| Icon packs, Monet themes, avatar shape, glass outline | Yes | No |
| Material 3 switches and cards | Yes | No |
| Persian (Solar Hijri) calendar, registration date | Yes | No |
| Reaction filter, shadow ban, message filters | Yes | No |
| Settings backup, diagnostics | Yes | No |
| Plugins (builds with -PmzgramPlugins=true) | Yes | No |
