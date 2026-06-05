# Lords of Minecraft Misc Mechanics

Small, self-contained server mechanics for Lords of Minecraft that are independent by default, but may share server-wide concepts such as users, accounts, and characters.

## Language

**Lords of Minecraft**:
The current iteration of the Minecraft server that these mechanics support. Often abbreviated as LoM.

**Mechanic**:
A small server feature that supports a specific Lords of Minecraft gameplay, moderation, or world-management need. Mechanics in this context do not directly interact with each other.
_Avoid_: module, plugin, feature

### People and Records

**User**:
The person who controls one or more Minecraft accounts. A user may have a user record that links their off-platform identities. Also called a player or customer.
_Avoid_: account

**Account**:
A UUID-identified Minecraft account controlled by a user.
_Avoid_: user, player, customer

**Character**:
A roleplay identity controlled through an account; an account may embody multiple characters, but only one at a time. A character cannot move from one account to another.
_Avoid_: account

**User Record**:
Persistent data related to a user, including moderation data and off-platform identities.
_Avoid_: user, account record

**Account Record**:
Persistent data related to an account, including moderation and account-ownership data. An account record may belong to a user record once the account is associated with a known user.
_Avoid_: account, player record

**Character Record**:
Persistent data related to a character, including moderation data. Each character record belongs to one account record.
_Avoid_: character, account record

### Elevators

**Elevator**:
A named transport system that moves accounts between its floors.

**Elevator Floor**:
A named stop in exactly one elevator, with a physical location and doors that open when the elevator arrives.
_Avoid_: floor, stop

**Elevator Travel**:
The movement of an elevator from its current elevator floor to a target elevator floor.
_Avoid_: elevator call, trip, ride

### DClock

**DClock**:
A Doom Clock mechanic for showing named countdowns or dynamic numeric values in the world. Originally tied to a server-wide storyline, but now treated as an independent reusable mechanic.
_Avoid_: Digital Clock

**Clock**:
A named countdown or dynamic numeric value managed by the DClock mechanic.
_Avoid_: timer

**Clock Display**:
An in-world digit display that renders the current value of exactly one clock; a clock may have multiple clock displays.
_Avoid_: display, digit display

**Clock Source**:
The upstream value that a clock reads to determine what it should show.
_Avoid_: source, variable, record

**Overtime**:
The state of a countdown clock after its target time has passed.
_Avoid_: expired, elapsed

### Leaderboards

**Leaderboard**:
A named ranking of accounts or characters by a numeric value.
_Avoid_: scoreboard, ranking board

**Leaderboard Subject**:
The account or character whose value is ranked on a leaderboard.
_Avoid_: player, account key

**Leaderboard Entry**:
A leaderboard subject's value and display name within a leaderboard.
_Avoid_: record, row

### Phonebook

**Phonebook**:
A character's list of other characters they know how to contact.
_Avoid_: contact list

**Phonebook Contact**:
A bidirectional relationship between two characters who know how to contact each other.
_Avoid_: contact entry, one-way contact

**Phonebook Exchange**:
An interaction where two accounts agree that the characters embodied at the moment of exchange become phonebook contacts and are listed in phonebooks.
_Avoid_: contact request, add flow

**Phonebook Seeking**:
A short-lived state where an account is trying to start a phonebook exchange by damaging another account.
_Avoid_: pending exchange, request timeout

**Phonebook Listing**:
A character's choice to be shown or hidden in other characters' phonebooks.
_Avoid_: reachability toggle, visibility flag

**Reachable Character**:
A listed character whose controlling account is online. A reachable character does not have to be the character currently embodied by the account.
_Avoid_: active contact, online contact

**Phonebook Call Target**:
The online account that currently controls the character selected from a phonebook.
_Avoid_: active character, called character

### Custom Paintings

**Custom Painting**:
A non-vanilla painting made available on the server by an external process, with its own artwork, size, title, and author.
_Avoid_: painting, painting definition

### Record Sync

**Account Sync**:
The process of keeping an account record aligned with the account's current data.
_Avoid_: database sync

**Character Sync**:
The process of keeping a character record aligned with the character's current data.
_Avoid_: database sync

### LoM Archive

**LoM Archive**:
A restored archive world from an older era of the server, where legacy items are removed and only MagicSpells items are allowed.

### Meta Overlay

**Meta Overlay Helper**:
A helper command used by the Meta Overlay mod to collect character data.
