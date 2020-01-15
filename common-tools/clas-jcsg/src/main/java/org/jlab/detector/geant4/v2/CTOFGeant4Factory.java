/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.detector.geant4.v2;

import eu.mihosoft.vrl.v3d.Vector3d;
import java.io.InputStream;
import java.util.ArrayList;
import static org.jlab.detector.hits.DetId.CTOFID;
import org.jlab.detector.units.SystemOfUnits.Length;
import org.jlab.detector.volume.G4Stl;
import org.jlab.detector.volume.G4World;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.geometry.prim.Line3d;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.geom.prim.Point3D;

/**
 *
 * @author kenjo
 */
public final class CTOFGeant4Factory extends Geant4Factory {

    private final int npaddles = 48;
    private final String ctofdbpath = "/geometry/ctof/ctof/";
    private final String caddbpath  = "/geometry/ctof/cad/";
    private final String trajdbpath = "/geometry/ctof/trajectory/";
    private final String tgdbpath   = "/geometry/target/";
    private double globalOffset = 0;
    private CTOFsurface surface = null;
    
    public CTOFGeant4Factory(ConstantProvider cp) {
        
        this.globalOffset = cp.getDouble(tgdbpath+"position", 0);
        double cadRadius  = cp.getDouble(caddbpath+"radius", 0); 
        double cadThick   = cp.getDouble(caddbpath+"thickness", 0); 
        double cadAngle   = cp.getDouble(caddbpath+"angle", 0);
        double cadOffset  = cp.getDouble(caddbpath+"offset", 0);
        double trajRadius = cp.getDouble(trajdbpath+"p0", 0);
        double trajZbend  = cp.getDouble(trajdbpath+"p1", 0);
        double trajSlope  = cp.getDouble(trajdbpath+"p2", 0);
        double trajLength = cp.getDouble(trajdbpath+"p3", 0);
//        System.out.println(trajRadius + " " + trajZbend + " " + trajSlope + " " + trajLength);
        motherVolume = new G4World("root");

        ClassLoader cloader = getClass().getClassLoader();

        for (String name : new String[]{"sc", "lgd"}) {
            for (int iscint = 1; iscint <= npaddles; iscint++) {
                CTOFpaddle component = new CTOFpaddle(cp, String.format("%s%02d", name, iscint),
                        cloader.getResourceAsStream(String.format("ctof/cad/%s%02d.stl", name, iscint)), iscint);
                component.scale(Length.mm / Length.cm);

                component.rotate("zyx", Math.toRadians(90+cadAngle), Math.toRadians(180), 0);
                
                Vector3d idealCenter   = getCenter(cadRadius, cadThick, cadAngle*(iscint-0.5));
                Vector3d currentCenter = component.center;
                Vector3d shift = currentCenter.clone().sub(idealCenter);
                component.translate(shift.x, shift.y, shift.z+cadOffset+globalOffset);
                component.setMother(motherVolume);

                if (name.equals("sc")) {
                    component.makeSensitive();
                    component.setId(CTOFID, iscint);
                }
            }
        }
        surface = new CTOFsurface(trajRadius, trajZbend+globalOffset, trajSlope, trajLength);
    }

    public Vector3d getCenter(double radius, double thickness, double angle){
        Vector3d cent = new Vector3d(radius+thickness/2.,0, 0);
        cent.rotateZ(Math.toRadians(angle));
        return cent;    
    }
    
    public Geant4Basic getPaddle(int ipaddle) {
        if (ipaddle < 1 || ipaddle > npaddles) {
            System.err.println("ERROR!!!");
            System.err.println("CTOF Paddle #" + ipaddle + " doesn't exist");
            System.exit(111);
        }
        return motherVolume.getChildren().get(ipaddle - 1);
    }

    public double getThickness(int ipaddle){
        CTOFpaddle pad = (CTOFpaddle) motherVolume.getChildren().get(ipaddle - 1);
        return pad.getThickness();
    }
    
    public double getRadius(int ipaddle){
        CTOFpaddle pad = (CTOFpaddle) motherVolume.getChildren().get(ipaddle - 1);
        return pad.center.magnitude();
    }
    
    public CTOFsurface getPolycone() {
        return this.surface;
    }
    
    public Point3D getSurfacePointAtZ(int ipaddle, double z) {
        CTOFpaddle pad = (CTOFpaddle) motherVolume.getChildren().get(ipaddle - 1);
        double x = pad.center.x*this.getPolycone().getRadius(z)/this.getRadius(ipaddle);
        double y = pad.center.y*this.getPolycone().getRadius(z)/this.getRadius(ipaddle);
        Point3D position = new Point3D(x,y,z);
        return position;
    }
            
    private class CTOFpaddle extends G4Stl {

        private final Line3d centerline;
        private final Vector3d center;
        private final double angle;
        private final double radius;
        private final double thickness;
        private final double length;
        private final double offset;
        
//        private final double zmin = -54.18, zmax = 36.26;

        CTOFpaddle(ConstantProvider cp, String name, InputStream stlstream, int padnum) {
            super(name, stlstream);
            angle = cp.getDouble(ctofdbpath+"angle", padnum-1);
            radius = cp.getDouble(ctofdbpath+"radius", padnum-1);
            thickness = cp.getDouble(ctofdbpath+"thickness", padnum-1);
            length = cp.getDouble(ctofdbpath+"length", padnum-1);
            offset = cp.getDouble(ctofdbpath+"offset", padnum-1);
            
            center = getCenter(radius, thickness, angle);
            centerline = new Line3d(new Vector3d(center.x, center.y, -length/2+offset+globalOffset), new Vector3d(center.x, center.y, length/2+offset+globalOffset));
        }

        @Override
        public Line3d getLineZ() {
            return new Line3d(centerline);
        }

        public double getThickness(){
            return thickness;
        }
    }

    public class CTOFsurface {
        private int nPlanes;
        private ArrayList<Double> radius = new ArrayList();
        private ArrayList<Double> planeZ = new ArrayList();

        /**
         * CTOF trajectory surface
         * the surface is described as a polycone with a cylindrical part and a conical part
         * 
         * @param rad:    radius of the cylindrical part
         * @param zbend:  z location of the transition between cylinder and cone
         * @param slope:  slope of the conical surface
         * @param length: length of the cylindrical and conical sections
         * 
         */
        public CTOFsurface(double rad, double zbend, double slope, double length) {
            nPlanes = 3;
            planeZ.add(zbend-length);
            planeZ.add(zbend);
            planeZ.add(zbend+length);
            radius.add(rad);
            radius.add(rad);
            radius.add(rad+slope*length);
//            System.out.println(planeZ.get(0) + " " + radius.get(0));
//            System.out.println(planeZ.get(1) + " " + radius.get(1));
//            System.out.println(planeZ.get(2) + " " + radius.get(2));
        }
        
        public ArrayList<Double> getRadiuses() {
            return radius;
        } 
        
        public ArrayList<Double> getZPlanes() {
            return planeZ;
        } 
        
        public int getPlanesNumber() {
            return nPlanes;
        } 
        
        public double getRadius(double z) {
            double r = 0;
            if(z<planeZ.get(0)) {
                r = radius.get(0);
            }
            else if(z>=planeZ.get(nPlanes-1)) {
                r = radius.get(nPlanes-1);
            }
            else{
                for(int i=0; i<nPlanes-1; i++) {
                    if(z>=planeZ.get(i) && z<planeZ.get(i+1)) {
                        r = radius.get(i) + (z-planeZ.get(i))*(radius.get(i+1)-radius.get(i))/(planeZ.get(i+1)-planeZ.get(i));
                        break;
                    }
                }
            }
            return r;

        }

    }
    
    public static void main(String[] args) {
        ConstantProvider cp = GeometryFactory.getConstants(DetectorType.CTOF);
        CTOFGeant4Factory factory = new CTOFGeant4Factory(cp);

        for (int ipad = 15; ipad <= 19; ipad++) {
            Geant4Basic pad = factory.getPaddle(ipad);
            Line3d line = pad.getLineZ();
            System.out.println(line);
        }
    }
}
