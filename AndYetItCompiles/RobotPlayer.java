package AndYetItCompiles;

import java.util.ArrayList;
import java.util.Random;

import battlecode.common.*;


public class RobotPlayer {

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

	// tanks and lumberjacks are somewhat risky things - dnt knw what to do
	static float[] buildOrder = {9, 16, 0, 0, 2}; // Ideal ratio: {gardener: soldier: lumberjack: tank: scouts}
	static RobotType type; // robot's type
	
	// similar to thdg's idea about different modes
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

	
	static void runScout() throws GameActionException {
		return;
	}
	
	
	static void runArchon() throws GameActionException {
		// Set combat to flee to a distance of 10
		combat = -100; // Archon is always in the flee mode

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

				// Check for death - if very less health left
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

				// Produce gardeners - conditions apply
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
				// TODO - improve if time permits
				wander(1);

				// Tries to shake a tree
				canPickTrees();

				// Save data about the game
				// saveData();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
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
	
	// this would be based on combat mode
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
					if(myLoc.distanceSquaredTo(newLoc) < -combat) {
						targetDirection = new Direction(newLoc, myLoc);
						signalLoc[0] = broadcastOne;
						signalLoc[1] = broadcastTwo;
					}
				}
			}
		}

		// for the archon - this is the thing that happens
		if(targetDirection == null || random.nextFloat() < .01f)
			targetDirection = randomDirection();

		if(!tryMove(targetDirection)) {
			targetDirection = null;
			wander(tries - 1);
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

		//Quick fix TODO clean this up
		//		float[] armyRatios;

		//		Calculate the number of each unit compared to the desired number
		float[] armyRatios = {(float)rc.readBroadcast(5) / buildOrder[0],
				((float)rc.readBroadcast(6) ) / buildOrder[1], (float)rc.readBroadcast(7) / buildOrder[2],
				(float)rc.readBroadcast(8) / buildOrder[3], (float)rc.readBroadcast(9) / buildOrder[4]}; // {gardener, soldier, lumberjack, tank, scout

		//store the best one to create
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
		// only if there are not enough lumberjacks


		// bring out the fucking tank
		if(bullets > 300 && bestRatio == 1 && rc.getRoundNum() % 10 != 0)
			bestRatio = 3;

		return bestRatio;
	}
	
	static void saveData() throws GameActionException {

		int unitCount = rc.readBroadcast(5) + rc.readBroadcast(6) + rc.readBroadcast(7) + rc.readBroadcast(8) + rc.readBroadcast(9);

		//rc.setTeamMemory(0, false ? 2 : 1);
		rc.setTeamMemory(1, unitCount);

		if(unitCount > 16) {
			// I dnt knw what to do -> may be some debugging drawing (taunt)
		}
	}
	
	
	static void runGardener() throws GameActionException {
		System.out.println("I'm a gardener!");

		// The code you want your robot to perform every round should be in this loop
		while (true) {

			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {

				// Listen for home archon's location
				int xPos = rc.readBroadcast(0);
				int yPos = rc.readBroadcast(1);
				MapLocation archonLoc = new MapLocation(xPos,yPos);

				// Generate a random direction
				Direction dir = randomDirection();

				// Randomly attempt to build a soldier or lumberjack in this direction
				if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .01) {
					rc.buildRobot(RobotType.SOLDIER, dir);
				} else if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < .01 && rc.isBuildReady()) {
					rc.buildRobot(RobotType.LUMBERJACK, dir);
				}

				// Move randomly
				tryMove(randomDirection());

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Gardener Exception");
				e.printStackTrace();
			}
		}
	}

	static void runSoldier() throws GameActionException {
		System.out.println("I'm an soldier!");
		Team enemy = rc.getTeam().opponent();

		// The code you want your robot to perform every round should be in this loop
		while (true) {

			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				MapLocation myLocation = rc.getLocation();

				// See if there are any nearby enemy robots
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

				// If there are some...
				if (robots.length > 0) {
					// And we have enough bullets, and haven't attacked yet this turn...
					if (rc.canFireSingleShot()) {
						// ...Then fire a bullet in the direction of the enemy.
						rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
					}
				}

				// Move randomly
				tryMove(randomDirection());

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}
	}

	static void runLumberjack() throws GameActionException {
		System.out.println("I'm a lumberjack!");
		Team enemy = rc.getTeam().opponent();

		// The code you want your robot to perform every round should be in this loop
		while (true) {

			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {

				// See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
				RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

				if(robots.length > 0 && !rc.hasAttacked()) {
					// Use strike() to hit all nearby robots!
					rc.strike();
				} else {
					// No close robots, so search for robots within sight radius
					robots = rc.senseNearbyRobots(-1,enemy);

					// If there is a robot, move towards it
					if(robots.length > 0) {
						MapLocation myLocation = rc.getLocation();
						MapLocation enemyLocation = robots[0].getLocation();
						Direction toEnemy = myLocation.directionTo(enemyLocation);

						tryMove(toEnemy);
					} else {
						// Move Randomly
						tryMove(randomDirection());
					}
				}

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
		return new Direction((float)Math.random() * 2 * (float)Math.PI);
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

		// First, try intended direction
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}

		// Now try a bunch of similar angles
		boolean moved = false;
		int currentCheck = 1;

		while(currentCheck<=checksPerSide) {
			// Try the offset of the left side
			if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
				rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
				return true;
			}
			// Try the offset on the right side
			if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
				rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
				return true;
			}
			// No move performed, try slightly further
			currentCheck++;
		}

		// A move never happened, so return false.
		return false;
	}

	/**
	 * A slightly more complicated example function, this returns true if the given bullet is on a collision
	 * course with the current robot. Doesn't take into account objects between the bullet and this robot.
	 *
	 * @param bullet The bullet in question
	 * @return True if the line of the bullet's path intersects with this robot's current position.
	 */
	static boolean willCollideWithMe(BulletInfo bullet) {
		MapLocation myLocation = rc.getLocation();

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

		// distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
		// This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
		// This corresponds to the smallest radius circle centered at our location that would intersect with the
		// line that is the path of the bullet.
		float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

		return (perpendicularDist <= rc.getType().bodyRadius);
	}

}
