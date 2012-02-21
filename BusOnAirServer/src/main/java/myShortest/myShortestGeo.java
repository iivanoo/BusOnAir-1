/* Versione di shortest path che calcola il percorso da un pto di partenza a uno di arrivo (non stazioni). 
 * Quindi include le walk per raggiungere la stazione di partenza dal pto di partenza e per raggiundere il
 * pto di destinazione dalla stazione di arrivo.
 * 
 * Specifiche da implementare: 
 * List<Direction> getDirections(
 * 		double lat1, 
 * 		double lon1, 
 * 		double lat2, 
 * 		double lon2, 
 * 		int departureTime, 
 * 		int minChangeTime, 
 * 		String criterion, 
 * 		int timeLimit)



TODO:
        source.numeroCambi = 0;
        TransientStop comparator
        gestione dei nodi Transientstop nella cache
        riscrivere loadsubgraph considerando nextwalk e prevwkal ( per i transientstop)
        
         PARTENZA:
         - creare uno stop transient per ogni possibile walk verso le vicine stazioni
         - questi stop avranno:
         		- come tempo il tempo di inizio della walk
         		- come nextinstation il primo stop nella stazione di arrivo della walk ke sia possibile prendere
				- applicare l'algo standard, facendo attenzione al numero di cambi contato nei primi passi
				
				
*/

package myShortest;

import domain.*;

import java.io.ObjectInputStream.GetField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Stack;

import json.Coordinate;
import json.DirectionRoute;
import json.DirectionWalk;

import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.*;

import utils.GeoUtil;

/**
 *
 * @author rashta
 */
public class myShortestGeo {
    private StopMediator cache;
    private int startTime;
    private int stopTime;
    private double lat1;
    private double lon1;
    private double lat2;
    private double lon2;
    private int walkLimit;
    private  PriorityQueue<TransientStop> startQueue;
    private  PriorityQueue<Direction> arrivalQueue;
   
    public myShortestGeo(int startTime, int _timeInterval, double lat1, double lon1, double lat2, double lon2, int walkLimit){
        cache = new StopMediator();   
        this.startTime = startTime;
        stopTime = startTime + _timeInterval;
        this.lat1 = lat1;
        this.lon1 = lon1;
        this.lat2 = lat2;
        this.lon2 = lon2;
        this.walkLimit = walkLimit;
        startQueue = new PriorityQueue<TransientStop>(10, new TransientStopComparator());
        arrivalQueue = new PriorityQueue<Direction>(10, new DirectionComparator());        
    }
    
    public StopMediator shortestPath(){
    	
    	createStartStops();
//    	System.out.print("\n\n**** startQueue ****");
//    	for(TransientStop ts : startQueue){
//    		System.out.print("\n" + ts);
//    	}
    	
    	for(TransientStop ts : startQueue){
    		loadSubgraph(ts.nextWalk);
    	}
            
        topologicalVisit();
        
        linkArrivalPoint();
        
        return cache;        
    }
    




	public Stop getShortestPath(Station dest){
        Stop arrivo = dest.getFirstStopsFromTime(startTime);
        if(arrivo != null)
            arrivo = cache.get(arrivo);
        
        while(arrivo != null && arrivo.prevSP == null){
            arrivo = arrivo.getNextInStation();
            if(arrivo != null)
                arrivo = cache.get(arrivo);            
        }
    
        return arrivo;
    }
    
    public String toString() {
    	String strPath = "";    
    	for(Direction dir : arrivalQueue){
	        String outPath = "";
    		Stop arrivo = dir.getStop();
	        if(arrivo != null){
	        	outPath = "(WALK: t:" + dir.getWalkTime() + "|arrtime:" + dir.getArrivalTime() + "|distwalk:" + dir.getDistance() + "|numchanges:" + dir.getNumChanges() + ")";
	            outPath = "(" + arrivo.getUnderlyingNode().getId() + ":ID" + arrivo.getId() + ":STAZID" + arrivo.getStazione().getId() + ":TIME" + arrivo.getTime() + ")-->" + outPath;
	
	            Stop arr = arrivo;
	            while(arr.prevSP != null){
	                arr = arr.prevSP;
	                outPath = "(" + arr.getType() + ":" + arr.getId()  + ":STAZID" + arr.getStazione().getId() + ":TIME" + arr.getTime() + ")-->" + outPath;                
	            }
	        }
	        strPath = strPath + "\n\n" + outPath; 
    	}
    	
        return strPath;

    }
    
    
//    public json.Directions getDirections(){
//    	json.Directions output = new json.Directions();
//
//    	Stop arrivo = getShortestPath();
//
//    	while(arrivo != null){
//    		output.add(getDirection(arrivo));
//    		
//    		//prende il successivo arrivalstop
//    		while(arrivo != null && arrivo.prevSP == null){
//	            arrivo = arrivo.getNextInStation();
//	            if(arrivo != null)
//	                arrivo = cache.get(arrivo);            
//	        }
//    	}
//    	
//    	return output;
//    }
//    
//    public json.Direction getDirection(Stop arrivo){
//    	json.Direction output = new json.Direction();
//    	
//    	
//        outPath = "(" + arrivo.getUnderlyingNode().getId() + ":ID" + arrivo.getId() + ":STAZID" + arrivo.getStazione().getId() + ":TIME" + arrivo.getTime() + ")";
//
//        Stop arr = arrivo;
//        while(!arr.equals(source)){
//            arr = arr.prevSP;
//            outPath = "(" + arr.getUnderlyingNode().getId() + ":ID" + arr.getId()  + ":STAZID" + arr.getStazione().getId() + ":TIME" + arr.getTime() + ")-->" + outPath;                
//        }
//    }
    
    
    
//    public ArrayList<Stop> getWeightedPath() {
//        ArrayList<Stop> stopList = new ArrayList<Stop>();
//        
//        Stop s = getShortestPath();
//
//        while(s != null){
//            stopList.add(s);
//            s = s.prevSP;
//        }
//
//        return stopList;
//    }    
    
    
	private void createStartStops() {
		Collection<Station> startStations = Stations.getStations().nearestStations(lat1, lon1, walkLimit);
		
//		System.out.print("\nLinked start stations: " + startStations.size());
		for(Station s : startStations){
//			System.out.println("\n");
//			System.out.println(s);
//			System.out.println("\n");
			double distance = GeoUtil.getDistance2(lat1, lon1, s.getLatitude(), s.getLongitude());
			int walktime = (int) (distance / 5.0 * 60);
			Stop startStop = s.getFirstStopsFromTime(startTime + walktime);
			startStop = cache.get(startStop);
//			System.out.print("\nstartStop: " + startStop);
			
			TransientStop ts = new TransientStop();
			ts.setTime(startStop.getTime() - walktime);
			ts.nextWalk = startStop;
			ts.setStazione(s);
			startStop.prevWalk = ts;

			startQueue.add(ts);			
		}	
	}
    
    private void linkArrivalPoint() {
    	// al momento linka solo il primo stop di ogni stazione più vicina di walkLimit meters
		Collection<Station> arrivalStations = Stations.getStations().nearestStations(lat2, lon2, walkLimit);
		
//		System.out.print("\nLnked arrival stations: " + arrivalStations.size());
		for(Station s : arrivalStations){
			Stop arrivalStop = getShortestPath(s);
//			System.out.print("\nsp: " + arrivalStop);
			arrivalQueue.add(new Direction(arrivalStop, lat2, lon2));			
		}		
	}
    
	public void loadSubgraph(Stop source){
		source = cache.get(source);
        
        Traverser graphTrav = source.getUnderlyingNode().traverse(
                Traverser.Order.BREADTH_FIRST,
                new StopExplorer(),
                ReturnableEvaluator.ALL, 
                RelTypes.NEXTINRUN,
                org.neo4j.graphdb.Direction.OUTGOING,
                RelTypes.NEXTINSTATION,
                org.neo4j.graphdb.Direction.OUTGOING);
        
        for (Node n : graphTrav){
        	connectNode(cache.get(n));
        }        
    }
    
	private void connectNode(Stop s){
        Stop nir = s.getNextInRun();
        Stop nis = s.getNextInStation();
        
//        if(s.getStazione().equals(dest)){
//            nir = null;
//            nis = null;                
//        }
        
        if(nir != null){
            nir = cache.get(nir);      
            s.nextInRun = nir;
            nir.prevInRun = s;
        }
        
        if(nis != null){
            nis = cache.get(nis);
            s.nextInStation = nis;
            nis.prevInStation = s;                    
        }		
	}
	
	
    public void topologicalVisit(){

        Stack<Stop> toVisit = new Stack<Stop>();
        
        for(Stop s : startQueue){
    		toVisit.push(s);
        }        
        
        while(!toVisit.isEmpty()){
            Stop s = toVisit.pop();
            Stop nir = s.nextInRun;
            Stop nis = s.nextInStation;
            Stop niw = s.nextWalk;
                        
            if(nir != null){
                // UPDATE Shortest path e cambi
                if(nir.prevSP == null){
                    nir.prevSP = s;
                    nir.numeroCambi = s.numeroCambi;
                } else if(s.numeroCambi < nir.numeroCambi){
                    nir.prevSP = s;
                    nir.numeroCambi = s.numeroCambi;
                }

                // Gestione visita topologica
                nir.prevInRun = null;
                if(nir.prevInStation == null && nir.prevWalk == null){
                    toVisit.push(nir);
                }
            }
            
            if(nis != null){
                // UPDATE Shortest path e cambi
                int cambio = 0;
                if((s.prevSP != null) && (s.prevSP.equals(s.getPrevInRun()))){
                    cambio = 1;
                }
                
                int cambiPerNis = s.numeroCambi + cambio;                
                if(nis.prevSP == null){
                    nis.prevSP = s;
                    nis.numeroCambi = cambiPerNis;
                } else if(cambiPerNis <= nis.numeroCambi){
                    nis.prevSP = s;
                    nis.numeroCambi = cambiPerNis;
                }

                // Gestione visita topologica
                nis.prevInStation = null;
                if(nis.prevInRun == null && nis.prevWalk == null){
                    toVisit.push(nis);
                }            
            }
            
            
            if(niw != null){
                // UPDATE Shortest path e cambi
                if(niw.prevSP == null){
                    niw.prevSP = s;
                    niw.numeroCambi = s.numeroCambi;
                } else if(s.numeroCambi < niw.numeroCambi){
                    niw.prevSP = s;
                    niw.numeroCambi = s.numeroCambi;
                }

                // Gestione visita topologica
                niw.prevWalk = null;
                if(niw.prevInStation == null && niw.prevInRun == null){
                    toVisit.push(niw);
                }
            }
            
            
        }
    }            
    
     public class StopExplorer implements StopEvaluator{
//        int count = 0;

        @Override
        public boolean isStopNode(TraversalPosition tp) {            
//            System.out.println( "\nVisited nodes: " + count++);
            Stop currentStop = cache.get(tp.currentNode());   
            if(currentStop.nextInRun != null || currentStop.nextInStation != null){
            	return true;
            } else if((currentStop.getTime() > stopTime)){
                return true;    
            }else{
                return false;
            }
        }
    }   


     public json.Directions getDirections(){
    	 json.Directions output = new json.Directions();
    	 for(Direction dir : arrivalQueue)
    		 output.add(getDirection(dir));
    	 
    	 return output;
     }
     
     public json.Direction getDirection(Direction dir) {
    	 
    	json.Direction output = new json.Direction();
//		int departureTime;
//		int arrivalTime;	//OK
//		int numChanges;		//OK
//		int minChangeTime;
//		List<DirectionRoute> dirRoute = new LinkedList<DirectionRoute>();
//	    List<DirectionWalk> dirWalk = new LinkedList<DirectionWalk>();
    	 
 		Stop arrivo = dir.getStop();
 		Station s_arrivo = arrivo.getStazione();

	    output.getWalks().add(new DirectionWalk(
	    		false, 
	    		dir.getWalkTime(), 
	    		(int) dir.getDistance() * 1000, 
	    		new json.Coordinate(s_arrivo.getLatitude(), s_arrivo.getLongitude()), 
	    		new json.Coordinate(dir.getLat(), dir.getLon())));
	    
    	output.setArrivalTime(dir.getArrivalTime());
    	output.setNumChanges(dir.getNumChanges());
    	

    	Stop tmp, prevtmp;
    	tmp = arrivo;
    	prevtmp = tmp.prevSP;
    	while(!prevtmp.getType().equals("TransientStop") && !prevtmp.getNextInRun().equals(tmp)){
    		
    	}

//        while(arr.prevSP != null){
//            arr = arr.prevSP;
//            outPath = "(" + arr.getType() + ":" + arr.getId()  + ":STAZID" + arr.getStazione().getId() + ":TIME" + arr.getTime() + ")-->" + outPath;                
//        }
//        strPath = strPath + "\n\n" + outPath; 
// 	
// 	
//         return strPath;

    	return output;
     }     

}