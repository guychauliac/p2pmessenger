package chabernac.space;

import java.util.ArrayList;

import chabernac.math.MatrixException;
import chabernac.utils.sort.FastArrayQSortAlgorithm;

public class World{
	public int mySize;
	public Shape[] myShapes;
	public int myPointShapeSize;
	public PointShape[] myPointShapes;
	private int myCurrentShape = 0;
	private int myCurrentPointShape = 0;
	private FastArrayQSortAlgorithm theSortAlgorithm = null;
	private ArrayList lightSources = new ArrayList();
	private TranslateManagerContainer myTranslateManagerContainer = new TranslateManagerContainer();


	public World(int aSize){
		this(aSize, 0);
	}
	
	public World(int aSize, int aPointShapeSize){
		mySize = aSize;
		myPointShapeSize = aPointShapeSize;
		initialize();
	}

	private void initialize(){
		myShapes = new Shape[mySize];
		myPointShapes = new PointShape[myPointShapeSize];
		theSortAlgorithm = new FastArrayQSortAlgorithm();
		clear();
	}

	public void clear(){
		for(int i=0;i<mySize;i++){
			myShapes[i] = null;
		}
		for(int i=0;i<myPointShapeSize;i++){
			myPointShapes[i] = null;
		}
		myCurrentShape = 0;
		myCurrentPointShape = 0;
	}

	public void addShape(Shape aShape){
		myShapes[myCurrentShape++] = aShape;
	}
	
	public void addPointShape(PointShape aPointShape){
		myPointShapes[myCurrentPointShape++] = aPointShape;
	}

	public void done() throws PolygonException{
		optimize();
		//affectLightning();
		calculateCenterPoints();
		calculateNormalVectors();
	}
	
	/*
	private void affectLightning(){
		LightSource theCurrentLight = null;
		Shape theCurrentShape = null;
		Polygon theCurrentPolygon = null;
		for(int i=0;i<lightSources.size();i++){
			theCurrentLight = (LightSource)lightSources.get(i);
			for(int j=0;j<myShapes.length;j++){
				theCurrentShape = myShapes[j];
				for(int k=0;k<theCurrentShape.myPolygons.length;k++){
					theCurrentPolygon = theCurrentShape.myPolygons[k];
					theCurrentLight.applyToPolygon(theCurrentPolygon);
				}
			}
		}
	}
	*/

	public void optimize(){
		if(myCurrentShape < mySize){
			Shape[] theTempShapes = new Shape[myCurrentShape];
			System.arraycopy(myShapes, 0, theTempShapes, 0, myCurrentShape);
      		myShapes = theTempShapes;
      		mySize = myCurrentShape;
		}
		if(myCurrentPointShape < myPointShapeSize){
			PointShape[] theTempShapes = new PointShape[myCurrentPointShape];
			System.arraycopy(myPointShapes, 0, theTempShapes, 0, myCurrentPointShape);
			myPointShapes = theTempShapes;
			myPointShapeSize = myCurrentShape;
		}
	}

	public void calculateCenterPoints(){
		for(int i=0;i<mySize;i++){
			myShapes[i].calculateCenterPoint();
		}
		for(int i=0;i<myPointShapeSize;i++){
			myPointShapes[i].calculateCenterPoint();
		}
	}

	public void calculateNormalVectors() throws PolygonException{
		for(int i=0;i<mySize;i++){
			myShapes[i].calculateNormalVectors();
		}
 	}

 	public void world2Cam(Camera aCamera) throws PolygonException, MatrixException{
	    for(int i=0;i<mySize;i++){
	      myShapes[i].world2Cam(aCamera);
	    }
		for(int i=0;i<myPointShapeSize;i++){
		  myPointShapes[i].world2Cam(aCamera);
		}
		for(int i=0;i<lightSources.size();i++){
			((LightSource)lightSources.get(i)).world2Cam(aCamera);
		}
	}

	public void clip2Frustrum(Frustrum aFrustrum) throws PolygonException{
	   for(int i=0;i<mySize;i++){
    	  myShapes[i].clip2Frustrum(aFrustrum);
 	   }
	   for(int i=0;i<myPointShapeSize;i++){
		  myPointShapes[i].clip2Frustrum(aFrustrum);
	   }
  	}

    public void sort() throws Exception{
		theSortAlgorithm.sort(myShapes);
		theSortAlgorithm.sort(myPointShapes);
	}
    
    public void addLightSource(LightSource aLightSource){
    	lightSources.add(aLightSource);
    }
    
    public void removeLightSource(LightSource aLightSource){
    	lightSources.remove(aLightSource);
    }
    
    public ArrayList getLightSources(){
    	return lightSources;
    }
    
    public TranslateManagerContainer getTranslateManagerContainer(){
		return myTranslateManagerContainer;
	}
}
