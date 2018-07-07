/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.rec.fvt.track.fit;

import java.util.ArrayList;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.trajectory.Trajectory;
/**
 *
 * @author ziegler
 */
public class TrackMatch {
    
    public List<ArrayList<ArrayList<MeasVecs.MeasVec>>> getListOfMeasurements(DataEvent event) {
        //System.out.println(" NEW EVENT READING FMT");
        if (event.hasBank("FMTRec::Clusters") == false) {
            return null;
        }
        List<ArrayList<ArrayList<MeasVecs.MeasVec>>> listOfMeas= new ArrayList<ArrayList<ArrayList<MeasVecs.MeasVec>>>();
        for(int s =0; s<6; s++) {
            ArrayList<ArrayList<MeasVecs.MeasVec>> listOfMeasSec= new ArrayList<ArrayList<MeasVecs.MeasVec>>();
            for(int l =0; l<6; l++) {
                listOfMeasSec.add(new ArrayList<MeasVecs.MeasVec>());
            }
            listOfMeas.add(listOfMeasSec);
        }
        
        DataBank bank = event.getBank("FMTRec::Clusters");
        MeasVecs mv = new MeasVecs();
        for (int i = 0; i < bank.rows(); i++) {
            int sector = bank.getByte("sector", i); 
            if(sector<1)
                continue;
            int layer = bank.getByte("layer", i); 
            int size = bank.getShort("size", i); 
            listOfMeas.get(sector-1).get(layer-1).add(mv.setMeasVec(layer-1, (double) bank.getFloat("centroid", i), bank.getInt("seedStrip", i), size));
        }
        //System.out.println("number of clusters "+bank.rows());
        return listOfMeas;
    }
    private MeasVecs mv = new MeasVecs();
    public List<ArrayList<MeasVecs.MeasVec>> matchDCTrack2FMTClusters(List<ArrayList<ArrayList<MeasVecs.MeasVec>>> listOfMeasurements, 
            List<Trajectory.TrajectoryStateVec> fMTTraj, int stripsOff, org.jlab.rec.fmt.Geometry fmtDetector) {
        List<ArrayList<MeasVecs.MeasVec>> measurements = new ArrayList<ArrayList<MeasVecs.MeasVec>>();
        if (listOfMeasurements == null || fMTTraj ==null) {
            return null;
        }
        double phi = Math.toDegrees(Math.atan2(fMTTraj.get(0).getY(), fMTTraj.get(0).getX()));
        int sector = this.getSector(phi);
        if(listOfMeasurements.get(sector-1).size()==0)
            return null; // matches in that sector.
        int[]L = new int[6];
        for(int l =0; l<6; l++) {
            L[l]=1;
            if(listOfMeasurements.get(sector-1).get(l).size()>1)
                L[l]=listOfMeasurements.get(sector-1).get(l).size();
        }
        int index=0;
        for(int l1 =0; l1<L[0]; l1++) {
            for(int l2 =0; l2<L[1]; l2++) {
                for(int l3 =0; l3<L[2]; l3++) {
                    for(int l4 =0; l4<L[3]; l4++) {
                        for(int l5 =0; l5<L[4]; l5++) {
                            for(int l6 =0; l6<L[5]; l6++) {
                                ArrayList<MeasVecs.MeasVec> measList = new ArrayList<MeasVecs.MeasVec>();
//                                int analLy = 0;
//                                if(listOfMeasurements.get(sector-1).get(analLy).size()>0) {
//                                    if(Math.abs(this.findNearestStrip(fMTTraj.get(l1).getX(), fMTTraj.get(l1).getY(), l1+1)-
//                                            listOfMeasurements.get(sector-1).get(analLy).get(l1).seed)<stripsOff)
//                                        measList.add(listOfMeasurements.get(sector-1).get(analLy).get(l1));
//                                }
                                measList.add(mv.setMeasVec(-1, (double) 0, 0, 1));
                                this.AddMeasToList(measList, listOfMeasurements.get(sector-1), fMTTraj, 0, l1, stripsOff, fmtDetector);
                                this.AddMeasToList(measList, listOfMeasurements.get(sector-1), fMTTraj, 1, l2, stripsOff, fmtDetector);
                                this.AddMeasToList(measList, listOfMeasurements.get(sector-1), fMTTraj, 2, l3, stripsOff, fmtDetector);
                                this.AddMeasToList(measList, listOfMeasurements.get(sector-1), fMTTraj, 3, l4, stripsOff, fmtDetector);
                                this.AddMeasToList(measList, listOfMeasurements.get(sector-1), fMTTraj, 4, l5, stripsOff, fmtDetector);
                                this.AddMeasToList(measList, listOfMeasurements.get(sector-1), fMTTraj, 5, l6, stripsOff, fmtDetector);
                                if(measList.size()>2) {
                                    measurements.add(measList);
                                }
                            }
                        }
                    }
                }
            }
        }
       
        return measurements;
    }
    /**
	 * Get the sector [1..6] from the phi value
	 * 
	 * @param phi the value of phi in degrees
	 * @return the sector [1..6]
	 */
	private int getSector(double phi) {
		// convert phi to [0..360]

		while (phi < 0) {
			phi += 360.0;
		}
		while (phi > 360.0) {
			phi -= 360.0;
		}

		if ((phi > 330) || (phi <= 30)) {
			return 1;
		}
		if (phi <= 90.0) {
			return 2;
		}
		if (phi <= 150.0) {
			return 3;
		}
		if (phi <= 210.0) {
			return 4;
		}
		if (phi <= 270.0) {
			return 5;
		}
		return 6;
        }
   

    private void AddMeasToList(ArrayList<MeasVecs.MeasVec> measList, ArrayList<ArrayList<MeasVecs.MeasVec>> listOfMeasurements, List<Trajectory.TrajectoryStateVec> fMTTraj, int analLy, int l1, 
            int stripsOff, org.jlab.rec.fmt.Geometry fmtDetector) {
      
        if(listOfMeasurements.get(analLy).size()>0) {
            if(Math.abs(fmtDetector.getClosestStrip(fMTTraj.get(analLy).getX(), fMTTraj.get(analLy).getY(), analLy+1)-
                    listOfMeasurements.get(analLy).get(l1).seed)<=stripsOff)
                measList.add(listOfMeasurements.get(analLy).get(l1));
        }
    }
    
}
