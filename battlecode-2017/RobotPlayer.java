package barancode;

import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Team enemy;
    static MapLocation[] enemyArchons; // Initial locations of enemy archons
    static int archonCount = 0; // Initial number of archons per team
    static int maintainBullets = 650; // Number of bullets to maintain (before donating)
    static MapLocation lastBroadcaster = null; // The most recent broadcaster to pursue
    static boolean runningAway = true; // Getting into position (running away) when first spawned
    static Direction runningDir; // Last direction robot was running away in
    static int numSpawned = 0; // Number of robots spawned by gardener/archon
    static int roundSpawned = 0; // The round in which robot was spawned
    static boolean spawnerGardener = false; // Whether the gardener's role is spawning robots (vs. planting)
    static Direction wandering; // Direction to wander in
    static MapLocation restLocation; // Permanent location for gardener that plants trees
    static int bytecodeFailures = 0; // Number of times robot ran out of bytecode
    static boolean processingComplete = true; // Whether robot finished processing last round
    static boolean justSpawned = false; // Whether the archon spawned a gardener the previous turn
    
    public static void initialize(RobotController rc){
    	RobotPlayer.rc = rc;
        RobotPlayer.enemy = rc.getTeam().opponent();
    	enemyArchons = rc.getInitialArchonLocations(enemy);
    	archonCount = enemyArchons.length;
        wandering = randomDirection();
        roundSpawned = rc.getRoundNum();
        if (rc.getType() == RobotType.GARDENER){
        	RobotInfo[] friends = rc.senseNearbyRobots(-1, rc.getTeam());
        	for (int i = 0; i < friends.length; i++){
            	if (friends[i].getType() == RobotType.ARCHON){
            		int degrees = Math.round(friends[i].getLocation().directionTo(rc.getLocation()).getAngleDegrees());
            		if (degrees % 2 == 0) spawnerGardener = true;
            		break;
            	}
            }
        }
    }

    public static void run(RobotController rc) throws GameActionException {
    	try {
	    	if (archonCount == 0) initialize(rc);
	        
	        while (true){
		        if (rc.getType() != RobotType.LUMBERJACK) dodgeBullets();
		        avoidLumberjacks();
		        if (bytecodeFailures == 0) shakeTrees();
		        donate();
		        checkEnding();
		
		        switch (rc.getType()) {
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
		            	runTank();
		            	break;
		            case SCOUT:
		            	runScout();
		            	break;
		        }
	        }
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
	}
    
    ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////
    //////////////////  ROBOT-SPECIFIC FUNCTIONS   /////////////////
    ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////

    static void runArchon() throws GameActionException {
        try {
        	Direction opposite = null;
        	
        	// Don't move if you spawned a gardener last turn, so it can measure its angle
        	if (!justSpawned){
	        	// Move away from enemy archons
	        	if (runningAway){ 
		        	float xsum = 0;
		        	float ysum = 0;
		        	for (int i = 0; i < enemyArchons.length; i++){
		        		Direction d = enemyArchons[i].directionTo(rc.getLocation());
		        		xsum += d.getDeltaX(1);
		        		ysum += d.getDeltaY(1);
		        	}
		        	opposite = new Direction(xsum, ysum);
		        	if (!tryMove(opposite)) runningAway = false;
	        	}
	        	
	        	// Random direction that tends to be away from enemies
	        	//Direction dir = new Direction((float)(Math.random()*5.759587 + 0.261799 + opposite.opposite().radians));
	        	//tryMove(dir);
	        	tryMove(randomDirection());
        	} else
        		justSpawned = false;
        	
        	// Spawn gardeners
        	Direction dir;
        	if (runningAway) dir = opposite.opposite().rotateRightRads((float)(Math.random()*Math.PI - Math.PI/2));
        	else dir = randomDirection();
        	dir = new Direction((float) (Math.PI/180* (Math.round(dir.getAngleDegrees()/2)*2 + (numSpawned % 2)) ));
            if (rc.canHireGardener(dir) && Math.random() < (1/(((float)rc.getRobotCount())/5+1)+2/3)/1.5) {
                rc.hireGardener(dir);
                numSpawned++;
                justSpawned = true;
            }
            
            Clock.yield();
        } catch (Exception e) {
            System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }

	static void runGardener() throws GameActionException {
        try {
        	// Every gardener should try to spawn at least one soldier
        	Direction dir = randomDirection();
        	if (numSpawned == 0 && rc.canBuildRobot(RobotType.SOLDIER, dir)){
        		rc.buildRobot(RobotType.SOLDIER, dir);
        		numSpawned++;
        	}
        	
        	waterTrees();
        	
        	// Move away from friendly archon, to give space
        	if (runningAway){
        		
	        	RobotInfo[] friends = rc.senseNearbyRobots(-1, rc.getTeam());
	        	if (rc.getRoundNum() - roundSpawned > 20) runningAway = false;
	        	for (int i = 0; i < friends.length; i++){
	            	if (friends[i].getType() == RobotType.ARCHON){
	            		runningDir = rc.getLocation().directionTo(friends[i].getLocation()).opposite();
	            		break;
	            	}
	            }
	        	if (runningDir != null) tryMove(runningDir, 30, 3);
	        	if (runningAway == false) restLocation = rc.getLocation();
	        	
        	} else {
        		
	        	if (!spawnerGardener){
	        		if (!rc.getLocation().equals(restLocation))
	        			tryMove(rc.getLocation().directionTo(restLocation));
	        		
		        	// Plant trees
		            if (Math.random() < (-1/(((float)rc.getRobotCount())/10+1)+1)/2){
		            	for (int i = 0; i < 6; i++){
		            		dir = new Direction((float)(i*Math.PI/3));
		                	if (rc.canPlantTree(dir)){
		                		rc.plantTree(dir);
		                		break;
		                	}
		            	}
		            }
	        	} else {
	        		tryMove(randomDirection());
	        		
		            // Spawn other robots
		            RobotType type;
		            if (numSpawned > 3) {
		            	double rand = Math.random();
		            	if (rand < 0.2) type = RobotType.LUMBERJACK;
		            	else if (rand < 0.6) type = RobotType.SOLDIER;
		            	else type = RobotType.TANK;
		            }
		            else if (numSpawned == 3) type = RobotType.TANK;
		            else if (numSpawned == 2) type = RobotType.SOLDIER;
		            else if (numSpawned == 1) type = RobotType.LUMBERJACK;
		            else type = RobotType.SOLDIER;
		            
		            dir = randomDirection();
		            if (rc.canBuildRobot(type, dir)){
		        		rc.buildRobot(type, dir);
		        		numSpawned++;
		        	} else {
		        		// Try again, with another direction
		        		dir = randomDirection();
			            if (rc.canBuildRobot(type, dir)){
			        		rc.buildRobot(type, dir);
			        		numSpawned++;
			            }
		        	}
	        	}
	        	
        	}
            
            Clock.yield();
        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }

    static void runSoldier() throws GameActionException {
        try {
        	if (processingComplete == false){
        		bytecodeFailures++;
        		System.out.println("soldier bytecode failure #" + bytecodeFailures);
        	}
        	processingComplete = false;
        	
        	RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
            if(robots.length > 0) {
            	
            	float dist = rc.getLocation().distanceTo(robots[0].getLocation());
            	Direction dir = rc.getLocation().directionTo(robots[0].getLocation());
            	
        		if (dist < 5) {
        			tryMove(dir.rotateRightDegrees(80));
        			tryMove(dir.rotateLeftDegrees(80));
        		} else
        			tryMove(dir);
        		
    			// Don't waste bullets shooting at trees
        		// Skip collision check if not enough bytecodes
        		if (bytecodeFailures > 1 || !checkBulletTreeCollision(dir, dist)){
        			if (rc.canFirePentadShot() && robots.length >= 5 && rc.getRoundNum() > 1000)
						rc.firePentadShot(dir);
        			else if (rc.canFireTriadShot() &&
	        				(robots[0].getType() == RobotType.ARCHON 
	        				|| robots[0].getType() == RobotType.TANK
	        				|| (rc.getRoundNum()/rc.getRoundLimit() > 1/2 && robots[0].getType() != RobotType.SCOUT)
	        				|| (robots.length >= 3 && rc.getRoundNum() > 500) ))
	            		rc.fireTriadShot(dir);
	            	else if (rc.canFireSingleShot())
	                    rc.fireSingleShot(dir);
        		}
        		
            } else {
            	if (bytecodeFailures < 3) readPriorities();
            	if (bytecodeFailures == 0) huntBroadcasters();
            	wander();
            }
            
            if (bytecodeFailures < 3) broadcastPriorities(robots);
            
            processingComplete = true;
            Clock.yield();
        } catch (Exception e) {
            System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }

    static void runLumberjack() throws GameActionException {
        try {
        	if (processingComplete == false) bytecodeFailures++;
        	processingComplete = false;
        	
            RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
            
            if (enemies.length > 0 && !rc.hasAttacked()) {
                rc.strike();
            } else {
            	// Avoid nearby friendlies
                RobotInfo[] friends = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
                if (friends.length > 0)
                    tryMove(rc.getLocation().directionTo(friends[0].getLocation()).opposite());
                
            	// Pursue nearby enemies
            	enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0)
                	tryMove(rc.getLocation().directionTo(enemies[0].getLocation()));
            	
                dodgeBullets();
                if (bytecodeFailures < 3) readPriorities();
            	if (bytecodeFailures == 0) huntBroadcasters();
            	chopTrees();
            	wander();
            }
            
            if (bytecodeFailures < 3) broadcastPriorities(enemies);
            
            processingComplete = true;
            Clock.yield();
        } catch (Exception e) {
            System.out.println("Lumberjack Exception");
            e.printStackTrace();
        }
    }
    
    static void runTank() throws GameActionException {
        try {
        	if (processingComplete == false) bytecodeFailures++;
        	processingComplete = false;
        	
        	RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
            if(robots.length > 0) {
            	
            	float dist = rc.getLocation().distanceTo(robots[0].getLocation());
            	Direction dir = rc.getLocation().directionTo(robots[0].getLocation());
            	
        		if (dist < 5) {
        			tryMove(dir.rotateRightDegrees(80));
        			tryMove(dir.rotateLeftDegrees(80));
        		} else
        			tryMove(dir);
        		
        		if (bytecodeFailures > 1 || !checkBulletTreeCollision(dir, dist)){ // don't waste bullets shooting at trees
	        		if (rc.canFirePentadShot() &&
	        				(robots[0].getType() == RobotType.ARCHON 
	        				|| robots[0].getType() == RobotType.TANK
	        				|| (rc.getRobotCount() < 10 && Math.random() < 1/3 && robots[0].getType() != RobotType.SCOUT)
	        				|| (rc.getRoundNum()/rc.getRoundLimit() > 1/2 && robots[0].getType() != RobotType.SCOUT)
	        				|| (robots.length >= 3 && rc.getRoundNum() > 500) )){
	            		rc.firePentadShot(dir);
	            	} else if (rc.canFireTriadShot()) {
	                    rc.fireTriadShot(dir);
	                } else if (rc.canFireSingleShot()) {
	                    rc.fireSingleShot(dir);
	                }
        		}
        		
            } else {
            	if (bytecodeFailures < 3) readPriorities();
            	if (bytecodeFailures == 0) huntBroadcasters();
            	trampleTrees();
                wander();
            }
            
            if (bytecodeFailures < 3) broadcastPriorities(robots);
            
            processingComplete = true;
            Clock.yield();
        } catch (Exception e) {
            System.out.println("Tank Exception");
            e.printStackTrace();
        }
    }
    
    static void runScout() throws GameActionException {
        try {
        	if (processingComplete == false) bytecodeFailures++;
        	processingComplete = false;
        	
        	RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
            if (robots.length > 0) {
            	Direction dir = rc.getLocation().directionTo(robots[0].getLocation());
            	
            	tryMove(rc.getLocation().directionTo(robots[0].getLocation()));
            	
        		if (bytecodeFailures > 1 || !checkBulletTreeCollision(dir, rc.getLocation().distanceTo(robots[0].getLocation()))){ // don't waste bullets shooting at trees
        			if (rc.canFirePentadShot() && robots.length >= 5 && rc.getRoundNum() > 1000)
						rc.firePentadShot(dir);
        			else if (rc.canFireTriadShot() &&
	        				(robots[0].getType() == RobotType.ARCHON 
	        				|| robots[0].getType() == RobotType.TANK
	        				|| (rc.getRoundNum()/rc.getRoundLimit() > 1/2 && robots[0].getType() != RobotType.SCOUT)
	        				|| (robots.length >= 3 && rc.getRoundNum() > 500) ))
	            		rc.fireTriadShot(dir);
	            	else if (rc.canFireSingleShot())
	                    rc.fireSingleShot(dir);
        		}
        		
            } else {
            	if (bytecodeFailures < 3) readPriorities();
            	if (bytecodeFailures == 0) huntBroadcasters();
            	wander();
            }
            
            if (bytecodeFailures < 3) broadcastPriorities(robots);
            
            processingComplete = true;
            Clock.yield();
        } catch (Exception e) {
            System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }
    
    ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////
    /////////////////////  GENERAL FUNCTIONS   /////////////////////
    ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////
    
	static void shakeTrees() throws GameActionException {
		TreeInfo[] trees = rc.senseNearbyTrees();
        if (trees.length > 0){
	        int tree = trees[0].getID();
	        if (rc.canShake(tree)) rc.shake(tree);
        }
	}
	
	static void waterTrees() throws GameActionException {
		TreeInfo[] trees = rc.senseNearbyTrees();
		float minHealth = 1;
		int minHealthID = -1;
        for (int i = 0; i < trees.length; i++){
        	if (trees[i].getHealth() / trees[i].getMaxHealth() < minHealth
        			&& rc.canWater(trees[i].getID())){
        		minHealthID = trees[i].getID();
        		minHealth = trees[i].getHealth() / trees[i].getMaxHealth();
        	}
        }
        if (minHealthID != -1) rc.water(minHealthID);
	}
	
	static void chopTrees() throws GameActionException {
		TreeInfo[] trees = rc.senseNearbyTrees();
        for (int i = 0; i < trees.length; i++){
        	if (trees[i].getTeam() == rc.getTeam()) continue;
	        int treeID = trees[i].getID();
	        if (rc.canChop(treeID)){
	        	rc.chop(treeID);
	        	break;
	        }
        }
        trampleTrees();
	}
	
	static void trampleTrees() throws GameActionException {
		TreeInfo[] trees = rc.senseNearbyTrees();
        for (int i = 0; i < trees.length; i++){
        	if (trees[i].getTeam() == rc.getTeam()) continue;
	        tryMove(rc.getLocation().directionTo(trees[i].getLocation()));
	        break;
        }
	}
	
	static void avoidLumberjacks() throws GameActionException {
		if (rc.getType() == RobotType.LUMBERJACK) return;
		
		RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (int i = 0; i < enemies.length; i++){
        	if (enemies[i].getType() == RobotType.LUMBERJACK
        			&& rc.getLocation().distanceTo(enemies[i].getLocation()) < 6.5){
        		tryMove(rc.getLocation().directionTo(enemies[i].getLocation()).opposite());
        		break;
        	}
        }
	}
	
	static void donate() throws GameActionException {
		if (rc.getTeamBullets() >= maintainBullets+10) rc.donate(10);
	}
	
	static void checkEnding() throws GameActionException {
		if (rc.getRoundLimit() - rc.getRoundNum() <= 1) rc.donate(rc.getTeamBullets());
	}
	
	static void broadcastPriorities(RobotInfo[] robots) throws GameActionException {
		for (int i = 0; i < robots.length; i++){
        	if (robots[i].getType() == RobotType.ARCHON || robots[i].getType() == RobotType.TANK || robots[i].getType() == RobotType.GARDENER){
        		int numExisting = rc.readBroadcastInt(0);
        		if (numExisting > 5) return;
        		rc.broadcastFloat(numExisting*2+1, robots[i].getLocation().x);
        		rc.broadcastFloat(numExisting*2+2, robots[i].getLocation().y);
        		rc.broadcastInt(0, numExisting+1);
        		return;
        	}
        }
	}
	
	static void readPriorities() throws GameActionException {
		int numExisting = rc.readBroadcastInt(0);
		float minDistance = GameConstants.MAP_MAX_HEIGHT + GameConstants.MAP_MAX_WIDTH;
		MapLocation target = null;
		for (int i = 0; i < numExisting; i++){
			float x = rc.readBroadcastFloat(i*2+1);
			float y = rc.readBroadcastFloat(i*2+2);
			MapLocation loc = new MapLocation(x, y);
			float d = rc.getLocation().distanceTo(loc);
			if (d <= 2){
				removePriority(i);
			} else if (d < minDistance){
				target = loc;
				minDistance = d;
			}
		}
		if (target != null) tryMove(rc.getLocation().directionTo(target));
	}
	
	static void removePriority(int id) throws GameActionException {
		// E.g. If ID=0, clears channels 1 and 2, shifts everything above that below, reduces numExisting
		int numExisting = rc.readBroadcastInt(0);
		rc.broadcastFloat(id*2+1, 0.0f);
		rc.broadcastFloat(id*2+2, 0.0f);
		for (int i = id; i < numExisting-1; i++){
			rc.broadcastFloat(i*2+1, rc.readBroadcastFloat(i*2+3));
			rc.broadcastFloat(i*2+2, rc.readBroadcastFloat(i*2+4));
		}
		rc.broadcastFloat(numExisting*2-1, 0.0f);
		rc.broadcastFloat(numExisting*2, 0.0f);
		rc.broadcastInt(0, numExisting-1);
	}
	
	static void huntBroadcasters() throws GameActionException {
		MapLocation[] broadcasters = rc.senseBroadcastingRobotLocations();
		float minDistance = GameConstants.MAP_MAX_HEIGHT + GameConstants.MAP_MAX_WIDTH;
		for (int i = 0; i < broadcasters.length; i++){
			float d = broadcasters[i].distanceTo(rc.getLocation());
			if (d < minDistance){
				lastBroadcaster = broadcasters[i];
				minDistance = d;
			}
		}
		if (lastBroadcaster != null){
			if (rc.getLocation().distanceTo(lastBroadcaster) <= 7) lastBroadcaster = null;
			else tryMove(rc.getLocation().directionTo(lastBroadcaster));
		}
	}
	
	static void wander() throws GameActionException {
		if (!tryMove(wandering)) wandering = randomDirection();
	}
	
	static boolean checkBulletTreeCollision(Direction bulletDir, float targetDist) throws GameActionException {
		TreeInfo[] trees = rc.senseNearbyTrees();
		for (int i = 0; i < trees.length; i++){
			// is bullet on collision course with tree?
			if (Math.abs(rc.getLocation().directionTo(trees[i].getLocation()).radiansBetween(bulletDir))
					<= Math.atan(trees[i].getRadius()/rc.getLocation().distanceTo(trees[i].getLocation()))
					&& rc.getLocation().distanceTo(trees[i].getLocation()) < targetDist){
				
				// if we have enough bullets, okay to shoot at trees
				if (trees[i].getTeam() == rc.getTeam() || rc.getTeamBullets() < 200)
					return true;
			}
		}
		return false;
	}
	
	////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////
	//////////////////  EXAMPLEPLAYER FUNCTIONS   //////////////////
	////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////

    /**
     * Returns a random Direction
     * 
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
        if (rc.hasMoved()){
        	return false;
        }
    	
    	if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            currentCheck++;
        }

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

        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta));

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
    
	static boolean trySidestep(BulletInfo bullet) throws GameActionException{
	    Direction towards = bullet.getDir();
	    return (tryMove(towards.rotateRightDegrees(90)) || tryMove(towards.rotateLeftDegrees(90)));
	}
	
	static void dodgeBullets() throws GameActionException {
	    BulletInfo[] bullets = rc.senseNearbyBullets();
	    for (BulletInfo bi : bullets) {
	        if (willCollideWithMe(bi)) {
	            trySidestep(bi);
	        }
	    }
	}
}
