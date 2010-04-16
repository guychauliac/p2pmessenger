/*
 * Created on 25-jul-2005
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package chabernac.space.buffer;

import chabernac.space.Polygon2D;
import chabernac.space.Vertex2D;

public class BufferTools {
	public static double[] findMinMaxY(Polygon2D aPolygon){
		double[] minmax = new double[2];
		Vertex2D[] theVertexes = aPolygon.getVertexes();
		minmax[1] = theVertexes[0].getPoint().getY();
		minmax[0] = minmax[1];
		double y;
		for(int i=1;i<theVertexes.length;i++){
			y =  theVertexes[i].getPoint().getY();
			if(y > minmax[1]){
				minmax[1] = y;
			}
			if(y < minmax[0]){
				minmax[0] = y;
			}
		}
		return minmax;
	}
}
