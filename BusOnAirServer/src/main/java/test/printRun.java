/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import utils.AlphanumComparator;

import domain.*;


/**
 *
 * @author rashta
 */
public class printRun {
	
 
    
    public static void main(String[] args) {     
		DbConnection.createEmbeddedDbConnection();
		
		

		Run r = Runs.getRuns().getRunById(11);
//		r.restoreRun();
		
//		CheckPoint cp = new CheckPoint(DbConnection.getDb().getNodeById(7803));
//		System.out.print(cp);
		System.out.print(r);
		
		DbConnection.turnoff();
		
    	
    }
    
}