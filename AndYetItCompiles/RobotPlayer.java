package AndYetItCompiles;
import java.util.ArrayList;
import java.util.Random;

import battlecode.common.*;


public strictfp class RobotPlayer {

	// channel info
	
	// 0 - rush
	// 1 - guess loc
	// 2 - Enemy Sighting x
	// 3 - Enemy Sighting y
	// 4 - Archon count
	// 5 - Gardener count
	// 6 - Soldier count
	// 7 - Lumberjack count
	// 8 - Tank Count
	// 9 - Scout Count
	// 10 - Alternate scouts
	// 11 - unused
	// 12+ - unused
	
	static RobotController rc; // RobotController object, used to get information about the robot


	static float[] buildOrder = {9, 16, 0, 0, 2}; // Ideal ratio: {gardener: soldier: lumberjack: tank: scouts}
	static RobotType type; // robot's type
	static int combat = -100; // set to 1 to go into combat, < 0 to avoid at that range, 0 to scout
	static Team enemy;
	static Team myTeam;

	// State Variables
	static Direction targetDirection = null; // Direction to move in
	static int[] signalLoc = new int[2]; // *****
	static boolean broadcastDeath = false; // Whether we have already broadcasted death
	static BulletInfo[] nearbyBullets; // all incoming bullets
	static boolean charge;

	// utility
	static Random random;

	/**
	 * run() is the method that is called when a robot is instantiated in the Battlecode world.
	 * If this method returns, the robot dies!
	 **/
	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException {
		// Get information about robot
		RobotPlayer.rc = rc; // get RobotController
		type = rc.getType(); // get type

		// Get teams
		enemy = rc.getTeam().opponent();
		myTeam = rc.getTeam();

		// Create random object
		random = new Random(rc.getID());




		// Switch on the RobotType: control passes to the method called here and remains there until the robot dies
		switch (type) {
			case ARCHON:
				runArchon();
				break;
			case GARDENER:
				runGardener();
				break;
			case SOLDIER:
				runSoldier();
				break;
			case LUMBERJACK:
				runLumberjack();
				break;
			case TANK:
				runSoldier();
				break;
			case SCOUT:
				runScout();
				break;
		}
	}

	/**
	 * Runs Archons: 20,000 bytecode, so do general operations here
	 **/
	static void runArchon() throws GameActionException {
		// Set combat to flee to a distance of 10
		combat = -100;

		// Broadcast unit creation
		rc.broadcast(4,rc.readBroadcast(4) + 1);

		// Find enemy archon starting locations
		MapLocation[] oppArchons = rc.getInitialArchonLocations(enemy);

		// Runs loops once a turn
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				

				
				// Check for incoming bullets
				nearbyBullets = rc.senseNearbyBullets();
				// TODO: FIND A BETTER WAY TO AVOID INCOMING BULLETS

				// Check for death
				if(!broadcastDeath && rc.getHealth() < 40) {
					rc.broadcast(4,rc.readBroadcast(4) - 1);
					broadcastDeath = true;
				}

				// Get my location
				MapLocation myLoc = rc.getLocation();

				// Guesses enemy location if not yet guessed
				if(rc.readBroadcast(1) == 0) { // no guess saved -> game just started
					// Use the enemy archon locations as guess
					MapLocation startLoc = rc.getInitialArchonLocations(enemy)[0];
					rc.broadcast(2, (int)startLoc.x);
					rc.broadcast(3, (int)startLoc.y);

					// Enemy location has been guessed
					rc.broadcast(1, 1);

				}

				// Produce gardeners
				if(chooseProduction(true) == 0) { // check if I should
					Direction dir = randomDirection();

					// Try all directions to produce gardener
					int num = 0;
					while (!rc.canHireGardener(dir) && num < 8) { // rotate until direction is clear
						num++;
						dir.rotateLeftDegrees(num * ((float) Math.PI / 4));
					}
					if (rc.canHireGardener(dir)) { // if possible, create
						rc.hireGardener(dir);
						rc.broadcast(5,rc.readBroadcast(5) + 1); // broadcast creation
					}
				}

				// Deal with nearby enemies
				// TODO SAVE A BETTER ENEMY LOCATION
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy); // Find all nearby enemies
				if (robots.length > 0) { // broadcast nearby enemy location
					rc.broadcast(2,(int)robots[0].location.x);
					rc.broadcast(3,(int)robots[0].location.y);
				}
				else {
					//TODO clear the broadcasts if old
					//TODO send units to an enemy location (spy on enemy brodcasts)
				}

				// Move randomly
				// TODO improve on this
				wander(1);

				// Tries to shake a tree
				canPickTrees();

				// Save data about the game
				saveData();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Runs Gardeners: 10,000 bytecode
	 **/
	static void runGardener() throws GameActionException {
		// Set combat to flee to a distance of __
		combat = -60;

		// Current build direction
		Direction buildAxis = null;

		// Whether we should water
		boolean water = false;
		
		int built = 1;

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				// Check for incoming bullets
				nearbyBullets = rc.senseNearbyBullets();

				// broadcast death
				if(!broadcastDeath && rc.getHealth() < 30) {
					rc.broadcast(5,rc.readBroadcast(5) - 1);
					broadcastDeath = true;
				}

				// call for help
				// TODO: FIX ENEMY LOCATION SAVING
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
				if (robots.length > 0) {
					rc.broadcast(2,(int)robots[0].location.x);
					rc.broadcast(3,(int)robots[0].location.y);
				}

				// Get build direction
				Direction dir;
				if(buildAxis == null) // No current direction
					dir = randomDirection(); // choose random direction
				else
					dir = buildAxis; // choose current direction

				// Attempt to build a robot
				int buildType = chooseProduction(false); // Get type of robot
				if (buildType == 1 && rc.canBuildRobot(RobotType.SOLDIER, dir)) { // type: soldier
					rc.buildRobot(RobotType.SOLDIER, dir);
					built++;
				} else if (buildType == 2 && rc.canBuildRobot(RobotType.LUMBERJACK, dir)) { // type: lumberjack
					rc.buildRobot(RobotType.LUMBERJACK, dir);
					built++;
				} else if (buildType == 4 && rc.canBuildRobot(RobotType.SCOUT, dir)) { // type: scout
					rc.buildRobot(RobotType.SCOUT, dir);
					rc.broadcast(10,1-rc.readBroadcast(10)); // broadcast scout creation
					built++;
				} else if (rc.canBuildRobot(RobotType.TANK, dir)) { // type: soldier
					rc.buildRobot(RobotType.TANK, dir);
					built++;
				}

				// Plant trees
				if((buildAxis != null || ( (!rc.isCircleOccupied(rc.getLocation().add(dir, 2.01f), 1)
						&& rc.senseNearbyTrees(5, rc.getTeam()).length == 0 ) && rc.onTheMap(rc.getLocation().add(dir, 2.01f), 1) ))) { // can plant trees

					for(int addDir = 60; addDir < 360; addDir += 60) { // try all tree directions
						if(rc.canPlantTree(dir.rotateRightDegrees(addDir)) && (built > 0 || rc.getRoundNum() > 400)) { // if can plant tree
							rc.plantTree(dir.rotateRightDegrees(addDir)); // plant the tree
							buildAxis = dir;
							water = true; // start watering
						}
					}
				}

				// Pick a tree to water
				TreeInfo treeInfo = null; // Tree object
				boolean canWaterTreeInfo = false; // whether we can water the tree
				TreeInfo[] trees = rc.senseNearbyTrees(); // get nearby trees
				for(TreeInfo tree : trees) {
					// Pick a new tree to target
					// TODO use a better algorithm for doing this. Maybe check for bullets or robots or pick closest one
					if(tree.getTeam() == myTeam) {
						boolean canWaterTree = rc.canWater(tree.ID);
						if(water && (treeInfo == null || (tree.health < treeInfo.health && (canWaterTree || !canWaterTreeInfo))
								|| (canWaterTree && !canWaterTreeInfo))) {
							treeInfo = tree;
							canWaterTreeInfo = canWaterTree;
						}
					}
				}

				// Water trees: if a tree is targeted move closer and water it if close enough
				if(treeInfo != null) { // If a tree is targeted
					if(canWaterTreeInfo) { // if we can water, water the tree
						rc.water(treeInfo.ID);
					}
					else { // if we can't water, move closer to the tree
						tryMove(directionTwords(rc.getLocation(), treeInfo.location));
						if(!rc.hasMoved()) { // if there is a tree to water but we can't water it and can't move closer, wander
							wander(1);
						}
					}
				}
				else {
					// Move randomly
					//TODO improve on this
					if(!rc.hasMoved())
						wander(1);
				}

				// Shake the closest tree
				canPickTrees();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Gardener Exception");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Runs Soldiers: 10,000 bytecode
	 **/
	static void runSoldier() throws GameActionException {
		// Set combat to 1: engage enemies
		combat = 1;

		// Broadcast creation
		rc.broadcast(6,rc.readBroadcast(6) + 1);

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				// Broadcast death - hahaha
				if(!broadcastDeath && rc.getHealth() < 30) {
					rc.broadcast(6,rc.readBroadcast(6) - 1);
					broadcastDeath = true;
				}

				// Get my location
				MapLocation myLoc = rc.getLocation();

				// See if there are any nearby enemy robots
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

				// If there are some...
				if (robots.length > 0) {
					// Find closest robot
					int closest = 0;
					float smallestDistance = Float.MAX_VALUE;
					for(int i = 0; i < robots.length; i++) { // loop through and find closest
						float distanceTo = robots[i].getLocation().distanceTo(myLoc);
						if(distanceTo < smallestDistance && robots[i].type != RobotType.ARCHON) {
							closest = i;
							smallestDistance = distanceTo;
						}
					}

					// Deal with closest robot
					if(robots[closest].type != RobotType.ARCHON) { // if it's not an archon
						// broadcast the closest robot location
						rc.broadcast(2,(int)robots[closest].location.x);
						rc.broadcast(3,(int)robots[closest].location.y);

						// Find the nearest bullets
						nearbyBullets = rc.senseNearbyBullets();

						//move closer to the enemy if it's not a soldier or lumberjack or we're greater than 0.7*sensorRad away
						if(robots[closest].type == RobotType.ARCHON || robots[closest].type == RobotType.GARDENER
								|| robots[closest].type == RobotType.SCOUT || smallestDistance > type.sensorRadius  * .5f) {
							
							// dnt want to go closer to the drunk guys

							// get enemy radius
							float enemyRad = robots[closest].type.bodyRadius;

							// check if we can move right next to the enemy
							if(smallestDistance - 1 - enemyRad <= type.strideRadius &&
									rc.canMove(directionTwords(myLoc, robots[closest].location), smallestDistance - 1 - enemyRad) ) { // if we can...
								if(safeToMove(rc.getLocation().add(directionTwords(myLoc, robots[closest].location),
										smallestDistance - 1 - enemyRad))) { // ... and it's safe...
									rc.move(directionTwords(myLoc, robots[closest].location), smallestDistance - 1 - enemyRad); // ...move
								}
							}
							else { // otherwise, try moving closer
								tryMove(directionTwords(myLoc, robots[closest].location));
							}
						}
						else // closest is soldier or lumberjack: move further away - run for ur life
							tryMove(directionTwords(robots[closest].location, myLoc));
						
						
						if (rc.canFirePentadShot()) {
							if(safeToShoot(myLoc.directionTo(robots[closest].location).rotateLeftDegrees(GameConstants.PENTAD_SPREAD_DEGREES))
									&& safeToShoot(myLoc.directionTo(robots[closest].location).rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES))
									&& safeToShoot(myLoc.directionTo(robots[closest].location))) {
								rc.firePentadShot(myLoc.directionTo(robots[closest].location));
							}
						}
						
						// Fire three shots against multiple enemies, gardeners, or archons
						if (rc.canFireTriadShot()) {
							if(safeToShoot(myLoc.directionTo(robots[closest].location).rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES))
									&& safeToShoot(myLoc.directionTo(robots[closest].location).rotateRightDegrees(GameConstants.TRIAD_SPREAD_DEGREES))
									&& safeToShoot(myLoc.directionTo(robots[closest].location))) {
								rc.fireTriadShot(myLoc.directionTo(robots[closest].location));
							}
						}

						// And we have enough bullets, and haven't attacked yet this turn...
						if (rc.canFireSingleShot()) {
							// ...Then fire a bullet in the direction of the enemy.
							if(safeToShoot(myLoc.directionTo(robots[closest].location))) {
								rc.fireSingleShot(myLoc.directionTo(robots[closest].location));
							}
						}
						
					}
					else { // Closest is an archon
						nearbyBullets = rc.senseNearbyBullets();
					}
				}
				else { // no robots nearby
					nearbyBullets = rc.senseNearbyBullets();
				}

				wander(1);

				// shake trees
				canPickTrees();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Runs Scouts: 10,000 bytecode
	 **/
	static void runScout() throws GameActionException {
		//broadcast unit creation -> this statement would be run only once
		rc.broadcast(9,rc.readBroadcast(9) + 1);

		// target only gardeners if 1
		float onlyTargetGardener = rc.readBroadcast(10);
		
		// scouts would engage only if they are huge in number otherwise only attack gardener
		combat = 1-(int)onlyTargetGardener; // if targeting gardeners, should not combat other units

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				//System.out.println(" ***** Scout " + rc.getID() + ": " + onlyTargetGardener + " *****");

				// broadcast death
				if(!broadcastDeath && rc.getHealth() < 8) {
					//rc.broadcast(9,rc.readBroadcast(9) - 1);
					broadcastDeath = true;
				}

				// get my location
				MapLocation myLocation = rc.getLocation();

				// Deal with nearby robots
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy); // See if there are any nearby enemy robots
				if (robots.length > 0) { // If there are some...

					// Find the closest robot (general) and closest gardener
					int closest = 0; // closest robot (general)
					int closestGardener = -1; // closest gardener
					float smallestDistance = Float.MAX_VALUE;
					float smallestGardenerDist = Float.MAX_VALUE;
					for(int i = 0; i < robots.length; i++) {
						float distanceTo = robots[i].getLocation().distanceTo(rc.getLocation());
						// we are avoiding the archon
						if(distanceTo < smallestDistance && (rc.getRoundNum() > 300 || robots[i].type != RobotType.ARCHON) ) {
							closest = i;
							smallestDistance = distanceTo;
						}
						if(robots[i].type == RobotType.GARDENER && (closestGardener==-1 || distanceTo<smallestGardenerDist)) {
							closestGardener = i;
							smallestGardenerDist = distanceTo;
						}
					}

					// Deal with the closest robots
					if(rc.getRoundNum() > 300 || robots[closest].type != RobotType.ARCHON) { // If past round 300, or the closest isn't an Archon

						rc.broadcast(2,(int)robots[closest].location.x);
						rc.broadcast(3,(int)robots[closest].location.y);

						nearbyBullets = rc.senseNearbyBullets();

						// Move based on type of sensed robot
						if(closestGardener!=-1) { // if there is a gardener nearby, we move to it: prioritize gardener attacks
							System.out.println("*** MOVING TO NEAREST GARDENER!! ***");
							MapLocation myLoc = rc.getLocation();

							// calculate the radius of the enemy
							float enemyRad = robots[closestGardener].type.bodyRadius;

							// check if we can move right next to the gardener
							if(smallestDistance - 1 - enemyRad <= rc.getType().strideRadius &&
									rc.canMove(directionTwords( myLoc, robots[closestGardener].location), smallestDistance - 1 - enemyRad) ) { // ...if we can...
								if(safeToMove(rc.getLocation().add(directionTwords( myLoc, robots[closestGardener].location),
										smallestDistance - 1 - enemyRad))) { // ...and it's safe...
									rc.move(directionTwords( myLoc, robots[closestGardener].location), smallestDistance - 1 - enemyRad); // ...move!
								}
							}
							else { // can't move right next to it, so just move closer
								tryMove(directionTwords( myLoc, robots[closestGardener].location));
							}
						}
						else if((onlyTargetGardener==0) && (robots[closest].type == RobotType.ARCHON || robots[closest].type == RobotType.GARDENER ||
								robots[closest].type == RobotType.SCOUT || smallestDistance > rc.getType().sensorRadius * .4)) {
							// there is no gardener nearby, and oTG = 0, so we combat all units; the closest is not a lumberjack or soldier, or we are greater than 0.7*sensorRad away
							System.out.println(" ***** THERE IS NO GARDENER SO COMBAT UNITS ***** ");
							MapLocation myLoc = rc.getLocation();

							//calculate the radius of the enemy
							float enemyRad = robots[closest].type.bodyRadius;

							// check if we can move right next to the gardener
							if(smallestDistance - 1 - enemyRad <= rc.getType().strideRadius &&
									rc.canMove(directionTwords( myLoc, robots[closest].location), smallestDistance - 1 - enemyRad) ) { // ...if we can...
								if(safeToMove(rc.getLocation().add(directionTwords( myLoc, robots[closest].location),
										smallestDistance - 1 - enemyRad))) { // ...and it's safe...
									rc.move(directionTwords( myLoc, robots[closest].location), smallestDistance - 1 - enemyRad); // ...move!
								}
							}
							else { // can't move right next to it, so just move closer
								tryMove(directionTwords( myLoc, robots[closest].location));
							}
						}
						else { // move further away from the enemy; 1 case
							// 1. oTG = 1, no gardener, and the closest is soldier or lumberjack that we are closer than 0.7 sensorRad to
							if (onlyTargetGardener == 0) {
								System.out.println(" ***** ERROR #2 ****");
								tryMove(directionTwords(robots[closest].location, rc.getLocation()));
							}
						}
						
						// If we have enough bullets, and haven't attacked yet this turn...
						if (rc.canFireSingleShot()) {
							// ...Then fire a bullet in the direction of the enemy.
							rc.fireSingleShot(myLocation.directionTo(robots[closest].location));
						}
						
					}
					else { // within round 300 and closest is archon
						nearbyBullets = rc.senseNearbyBullets();
					}
				}
				else { // no robots nearby
					nearbyBullets = rc.senseNearbyBullets();
				}

				// Move randomly; generally occurs when
				// oTG = 0 and no gardener nearby
				if(!rc.hasMoved())
					wander(1);

				// shake nearby trees
				canPickTrees();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Scout Exception");
				e.printStackTrace();
			}
		}
	}

	static void runLumberjack() throws GameActionException {
		// get the enemy team
		Team enemy = rc.getTeam().opponent();

		// scout for trees - be safe than sorry (THE DRUNK GUYS ARE IN THE TOWN)
		combat = 0;

		// trees to chop down
		int targetTree = -1;

		//broadcast unit creation
		rc.broadcast(7,rc.readBroadcast(7) + 1);
		
		boolean doNotMove = false;

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				// sense nearby bullets
				charge = false;
				doNotMove = false;
				nearbyBullets = rc.senseNearbyBullets();

				// broadcast death
				if(!broadcastDeath && rc.getHealth() < 20) {
					rc.broadcast(7,rc.readBroadcast(7) - 1);
					broadcastDeath = true;
				}

				// Attack any nearby robots
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy); // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
				
				// Move towards the closest enemy robot
				if (robots.length > 0) {
					// find closest
					int closest = 0;
					float smallestDistance = Float.MAX_VALUE;
					for (int i = 0; i < robots.length; i++) {
						float distanceTo = robots[i].getLocation().distanceTo(rc.getLocation());
						if (distanceTo < smallestDistance) {
							closest = i;
							smallestDistance = distanceTo;
						}
					}

					//broadcast its location
					rc.broadcast(2, (int) robots[closest].location.x);
					rc.broadcast(3, (int) robots[closest].location.y);

					//and move closer to it
					charge = true;
					tryMove(directionTwords(rc.getLocation(), robots[closest].location));
				}
			
				
				
				robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy); // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
				if(robots.length > 0 && !rc.hasAttacked()) { // attack them
					rc.strike();
				}
				
				// CHOP NEUTRAL/ENEMY TREES
				// Get the info on the targeted tree
				TreeInfo treeInfo = null;
				if(targetTree != -1) { // we have a target tree
					if(rc.canSenseTree(targetTree)) // it is within sensor radius
						treeInfo = rc.senseTree(targetTree); // sense the tree
					else
						targetTree = -1; // unset the target
				}
				if(targetTree == -1){ // no target tree
					TreeInfo[] trees = rc.senseNearbyTrees(); // get nearby trees
					if(trees.length > 0) { // if there is a tree nearby
						// choose closest tree not on our team
						for(int i = 0; i < trees.length; i++) {
							if(treeInfo==null && trees[i].team != rc.getTeam() ) {
								treeInfo = trees[i];
								targetTree = treeInfo.ID;
							}
						}
					}
				}

				// if a tree is targeted move closer to it or chop it
				if(treeInfo != null) {
					//System.out.println("try chop " + treeInfo.ID);
					if(rc.canChop(treeInfo.ID)) { // chop the tree
						rc.chop(treeInfo.ID);
						doNotMove = true;
					}
					else if (!doNotMove && !rc.hasMoved()) { // move towards it
						tryMove(directionTwords(rc.getLocation(), treeInfo.location));
					}
				}

				// Move randomly
				// TODO improve on this
				if(!doNotMove && !rc.hasMoved()) {
					wander(1);
				}

				canPickTrees();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Lumberjack Exception");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Returns a random Direction
	 * @return a random Direction
	 */
	static Direction randomDirection() {
		return new Direction(random.nextFloat() * 2 * (float)Math.PI);
	}

	/**
	 * Returns a Direction from objLoc to towardsLoc
	 * @return a Direction from objLoc to towardsLoc
	 */
	static Direction directionTwords(MapLocation objLoc, MapLocation towardsLoc) {
		return new Direction(objLoc, towardsLoc);
	}

	/**
	 * Wanders randomly based on combat
	 */
	static void wander(int tries) throws GameActionException {
		if(tries <= 0)
			return;
		
		MapLocation myLoc = rc.getLocation();
		
		//Try and move towards a tree with bullets
		MapLocation treeLoc = moveToBulletTree();
		if(combat >= 0 && treeLoc != null)
			targetDirection = new Direction(myLoc, treeLoc);

		if((combat != 0 && targetDirection != null)) {
			//run to
			int broadcastOne = rc.readBroadcast(2);
			int broadcastTwo = rc.readBroadcast(3);
			if( (broadcastOne != 0 || broadcastTwo != 0) && (signalLoc[0] != broadcastOne || signalLoc[1] != broadcastTwo)) {
				MapLocation newLoc = new MapLocation(broadcastOne, broadcastTwo);
				if(combat > 0) {

					targetDirection = new Direction(myLoc, newLoc);
					signalLoc[0] = broadcastOne;
					signalLoc[1] = broadcastTwo;
				}
				else if(combat < 0) {
					// repellant effect
					if(myLoc.distanceSquaredTo(newLoc) < -combat) {
						targetDirection = new Direction(newLoc, myLoc);
						signalLoc[0] = broadcastOne;
						signalLoc[1] = broadcastTwo;
					}
				}
			}
		}
		
		if(targetDirection == null || random.nextFloat() < .01f)
			targetDirection = randomDirection();

		// try to move in that dir
		if(!tryMove(targetDirection)) {
			targetDirection = null;
			wander(tries - 1);
		}

	}

	/**
	 * Returns the unit that should be produced based on current game conditions
	 * 0: Gardener
	 * 1: Soldier
	 * 2: Lumberjack
	 * 3: Tank
	 * 4: Scout
	 */
	static int chooseProduction(boolean archon) throws GameActionException {

		// Trade in for VP at the last round
		float bullets = rc.getTeamBullets();
		float bulletsPerVp = (float)(7.5 + rc.getRoundNum() * 12.5 / 3000);
		if((GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints()) * bulletsPerVp   <= bullets 
				|| rc.getRoundNum() >= rc.getRoundLimit() - 1) {
			rc.donate(bullets);
		}
		else if(bullets > 400) {
			if(rc.readBroadcast(5) < 3 && archon)
				return 0;
			if(bullets > 1000) {
				rc.donate((float)bulletsPerVp);
			}
		}

		float[] armyRatios = {(float)rc.readBroadcast(5) / buildOrder[0],
				((float)rc.readBroadcast(6) ) / buildOrder[1], (float)rc.readBroadcast(7) / buildOrder[2],
				(float)rc.readBroadcast(8) / buildOrder[3], (float)rc.readBroadcast(9) / buildOrder[4]}; // {gardener, soldier, lumberjack, tank, scout

		int bestRatio = -1;

		//iterate through each unit type and select the best one to produce next
		for(int i = ((archon||rc.readBroadcast(4)>0)?0:1); i < armyRatios.length; i++) {
			//System.out.println(" " + armyRatios[i] + " " + armyRatios[bestRatio]);
			if(bestRatio<0 || (armyRatios[i] < armyRatios[bestRatio] && buildOrder[i]!=0)) {
				bestRatio = i;
			}
		}

		// TODO: Determine whether there are trees on the map
		if(archon && rc.readBroadcast(5)==0) bestRatio = 0;
		else if(bestRatio != 0 && rc.senseNearbyTrees(6, Team.NEUTRAL).length > 0 && rc.readBroadcast(7) < rc.readBroadcast(6) + 1) bestRatio = 2;


		
		if(bullets > 300 && bestRatio == 1 && rc.getRoundNum() % 10 != 0)
			bestRatio = 3;

		return bestRatio;
	}

	/**
	 * Checks whether robot can shake any trees and if so, shakes the tree
	 */
	public static void canPickTrees() throws GameActionException {
		TreeInfo[] nearbyTrees = rc.senseNearbyTrees();
		for(TreeInfo tree : nearbyTrees) {
			if(tree.containedBullets > 0 && !rc.hasAttacked() && rc.canShake(tree.ID)) {
				rc.shake(tree.ID);
			}
		}
	}
	
	
	//get the location of the nearest tree with bullets
	public static MapLocation moveToBulletTree() throws GameActionException {
		TreeInfo[] nearbyTrees = rc.senseNearbyTrees();
		for(TreeInfo tree : nearbyTrees) {
			if(tree.containedBullets > 0) {
				return tree.location;
			}
		}
		return null;
	}

	/**
	 * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
	 *
	 * @param dir The intended direction of movement
	 * @return true if a move was performed
	 * @throws GameActionException
	 */
	static boolean tryMove(Direction dir) throws GameActionException {
		return tryMove(dir,20,3);
	}

	/**
	 * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
	 *
	 * @param dir The intended direction of movement
	 * @param degreeOffset Spacing between checked directions (degrees)
	 * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
	 * @return true if a move was performed
	 * @throws GameActionException
	 */
	static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

		if(rc.hasMoved())
			return false;
		
		// First, try intended direction
		if (rc.canMove(dir) && safeToMove(rc.getLocation().add(dir, type.strideRadius))) {
			rc.move(dir);
			return true;
		}

		// Now try a bunch of similar angles
		int currentCheck = 1;

		while(currentCheck<=checksPerSide) {
			// Try the offset of the left side
			if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))
					&& safeToMove(rc.getLocation().add(dir.rotateLeftDegrees(degreeOffset*currentCheck), type.strideRadius))) {
				rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
				return true;
			}
			// Try the offset on the right side
			if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))
					&& safeToMove(rc.getLocation().add(dir.rotateRightDegrees(degreeOffset*currentCheck), type.strideRadius))) {
				rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
				return true;
			}
			// No move performed, try slightly further
			currentCheck++;
		}

		// A move never happened, so return false.
		return false;
	}

	static boolean safeToMove(MapLocation moveLoc) {
		if(charge || !willBulletHitMe(moveLoc)) {
			if (type != RobotType.TANK) return true;
			else {
				// dnt want the tank to trample our own tree
				MapLocation myLoc = rc.getLocation();
				MapLocation nextLoc = myLoc.add(myLoc.directionTo(moveLoc), type.strideRadius);
				TreeInfo trees[] = rc.senseNearbyTrees(-1, myTeam);
				for(int i=0; i<trees.length; i++) {
					if(nextLoc.distanceTo(trees[i].location)<trees[i].radius+type.bodyRadius) return false;
				}
				return true;
			}
		}
		return false;
	}

	static boolean willBulletHitMe(MapLocation mapLocation){
		// i<22 for - sometimes byte code exceeded
		for(int i = 0; i < nearbyBullets.length && i < 22; i++) {
			if(willCollideWithMe(nearbyBullets[i],mapLocation)) {
				return true;
			}
		}
		return false;
	}

	static void saveData() throws GameActionException {
		
		int unitCount = rc.readBroadcast(5) + rc.readBroadcast(6) + rc.readBroadcast(7) + rc.readBroadcast(8) + rc.readBroadcast(9);

		//rc.setTeamMemory(0, false ? 2 : 1);
		rc.setTeamMemory(1, unitCount);
		
		if(unitCount > 16) {
			// may give a sign of optimality
		}
	}

	/**
	 * A slightly more complicated example function, this returns true if the given bullet is on a collision
	 * course with the current robot. Doesn't take into account objects between the bullet and this robot.
	 *
	 * @param bullet The bullet in question
	 * @return True if the line of the bullet's path intersects with this robot's current position.
	 */
	static boolean willCollideWithMe(BulletInfo bullet, MapLocation myLocation) {
		// Get relevant bullet information
		Direction propagationDirection = bullet.dir;
		MapLocation bulletLocation = bullet.location;

		// Calculate bullet relations to this robot
		Direction directionToRobot = bulletLocation.directionTo(myLocation);
		float distToRobot = bulletLocation.distanceTo(myLocation);
		float theta = propagationDirection.radiansBetween(directionToRobot);

		// If theta > 90 degrees, then the bullet is traveling away from us and we can break early
		if (Math.abs(theta) > Math.PI/2) {
			return false;
		}

		// check whether line segment (bulletLocation, nextBulletLoc) intersects with any part of the circle centered at myLocation and radius rc.getType().bodyRadius
		// A: bullet's current position, B: bullet's next position, C: projection of circle's center onto bullet's path
		MapLocation nextBulletLoc = bulletLocation.add(propagationDirection, bullet.getSpeed()); // get B
		float dist = (float)Math.abs(distToRobot*Math.cos(theta)); // get distance to C

		// find perpendicular from center to segment
		if(bulletLocation.distanceTo(nextBulletLoc)<dist) { // bullet does not reach C, check if B is in the circle
			return (nextBulletLoc.isWithinDistance(myLocation, type.bodyRadius) || bulletLocation.isWithinDistance(myLocation, type.bodyRadius));
		}
		else { // bullet does reach C, check if it is in the circle
			// distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
			// This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
			// This corresponds to the smallest radius circle centered at our location that would intersect with the
			// line that is the path of the bullet.
			float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)
			return (perpendicularDist <= rc.getType().bodyRadius);
		}
	}
	
	// chk that we are not damaging our own players or trees
	static boolean safeToShoot(Direction dir) {
		MapLocation myLoc = rc.getLocation();
		RobotInfo robots[] = rc.senseNearbyRobots(-1);
		TreeInfo trees[] = rc.senseNearbyTrees(-1);
		int robotSpot = 0;
		int treeSpot = 0;
		while(robotSpot < robots.length && treeSpot < trees.length) {
			if(robots[robotSpot].location.distanceTo(myLoc) < trees[treeSpot].location.distanceTo(myLoc)) { // check next robot
				if(willShotCollideWithRobot(dir, robots[robotSpot])) { // will collide
					if(robots[robotSpot].team==enemy) return true;
					else return false;
				}
				robotSpot++;
			}
			else { // check next tree
				if(willShotCollideWithTree(dir, trees[treeSpot])) {
					if(trees[treeSpot].team==enemy) return true;
					else return false;
				}
				treeSpot++;
			}
		}
		if(robotSpot==robots.length) return false; // no robots left, and we didn't hit any enemies
		else {
			for(int i=robotSpot; i<robots.length; i++) {
				if(willShotCollideWithRobot(dir, robots[i])) {
					if(robots[robotSpot].team==enemy) return true;
					else return false;
				}
			}
		}
		return false;
	}

	static boolean willShotCollideWithTree(Direction dir, TreeInfo unit) {
		// Get relevant bullet information
		Direction propagationDirection = dir;
		MapLocation bulletLocation = rc.getLocation(); // the bullet starts from our location since we fired it

		// Calculate bullet relations to this robot
		Direction directionToRobot = bulletLocation.directionTo(unit.getLocation());
		float distToRobot = bulletLocation.distanceTo(unit.getLocation());
		float theta = propagationDirection.radiansBetween(directionToRobot);

		// If theta > 90 degrees, then the bullet is traveling away from the unit and we can break early
		if (Math.abs(theta) > Math.PI/2) {
			return false;
		}

		// distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
		// This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
		// This corresponds to the smallest radius circle centered at the unit's location that would intersect with the
		// line that is the path of the bullet.
		float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)
		return (perpendicularDist <= unit.radius);
	}

	static boolean willShotCollideWithRobot(Direction dir, RobotInfo unit) {
		// Get relevant bullet information
		Direction propagationDirection = dir;
		MapLocation bulletLocation = rc.getLocation(); // the bullet starts from our location since we fired it

		// Calculate bullet relations to this robot
		Direction directionToRobot = bulletLocation.directionTo(unit.getLocation());
		float distToRobot = bulletLocation.distanceTo(unit.getLocation());
		float theta = propagationDirection.radiansBetween(directionToRobot);

		// If theta > 90 degrees, then the bullet is traveling away from the unit and we can break early
		if (Math.abs(theta) > Math.PI/2) {
			return false;
		}

		// distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
		// This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
		// This corresponds to the smallest radius circle centered at the unit's location that would intersect with the
		// line that is the path of the bullet.
		float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)
		return (perpendicularDist <= unit.getType().bodyRadius);
	}

}
