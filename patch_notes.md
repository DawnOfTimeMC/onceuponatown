## 🏘️ Once Upon a Town v0.0.12 | Reworked questing, side activities, improved builder AI !
###  Overhauled questing system:
- The questing system is now a dedicated widget in the interface instead of being this random mess randomly appearing
- Added a ton of new datadriven prerequisites to allow a deep customization for quest, making each quest appearing in specific cases instead of being random. For example, a quest asking for wood if the supplies are running low, or a quest of lore appearing only because you helped the village
- Removed the previous quest, there is new 2 delivery quests and 2 notes that should appear in the early game
- There is no longer the need of sending the resources through the quest interface to validate them, the system is going to automatically detect if you have the needed resources inside your inventory and the reward is going to be sent in your inventory
### Side activities:
- When the builder is done working on the construction queue, he will randomly pick a task in the village, for now between crafting and mining
- The system is 100% customizable and allow to place custom buildings, custom object in hand and custom animation type (mine/craft)
- This will be extended and improved later with new NPC and more activities like choping or planting
### AI enhancement:
- The builder is now producing sounds while building, checking it's map and while doing activities
- Can now look upside and downward, making the construction and side activity animations way better
- Should be using more straight forward path while navigating inside the village and when upgrading buildings thanks to a few tweak in it's pathfinding. He is building and upgrading faster buildings, making the pace of the mod smoother
- Now mortal, if it drowns or suffocates it should respawn and take the task again, helping the system to keep going when the AI is a bit glitchy. You can also kill it if you see him stuck to reset it's behavior
### Summary widget reworked:
- Reworked the village summary widget to make it easier to read by collapsing all the production and transformation to a simple list of resources/min. Also added a log system that helps you to understand what is happening in the village even without checking the townhall
- Added a button to on/off the broadcast of the village logs so you can keep a track or no of what is happening
- Reworked the way the resources or displayed, items that will be produced later in the village will have a lock icon on top of it, indicating that upgrading your buildings will unlocks new recipes
### Gameplay changes:
- Changed a lot of era prerequisites to make these more unique and coherent 
- Added the era 3 that raises the total weight to 50 for a max village
- The weight is now per builder instead of per era, reduced a lot for a lot of early builds to make early game less frustrating
- The roads are now more focus on a purpose, there is roads that will add a lot of residents, other for jobs and some that tend to build neighborhood, I made this change to make villages more compact and less random
- Changed the road placement system, making the NPC trying to make the village grow from the center instead of extending branches of the village far away
- Made some trades more balanced and added new trades
- The NPC and the herds are now eating twice a day instead of one, making the era progress smoother instead of needing to wait one day each time. Also this is now 100% datadriven, helping you to customize the eating frequency of the village
### New buildings:
- Carpenter with 7 upgrades
- Wheat farm with 6 upgrades
- Street 11 (residential)
- Changed some buildings levels to make these more coherent between the others
