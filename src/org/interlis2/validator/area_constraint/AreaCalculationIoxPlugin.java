package org.interlis2.validator.area_constraint;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxValidationConfig;
import ch.interlis.iox_j.jts.Iox2jtsException;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.iox_j.validator.ObjectPool;
import ch.interlis.iox_j.validator.Value;
import static ch.interlis.iox_j.jts.Iox2jts.surface2JTS;
import static ch.interlis.iox_j.jts.Iox2jts.multisurface2JTS;

import java.util.*;

import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.MultiPolygon;

public class AreaCalculationIoxPlugin implements InterlisFunction {

	private LogEventFactory logger=null;
	@Override
	public void init(TransferDescription td, Settings settings,
			IoxValidationConfig validationConfig, ObjectPool objectPool,
			LogEventFactory logEventFactory) {
		logger=logEventFactory;

	}

	/**
	 *
	 * @param validationKind See original Doc.
	 * @param usageScope See original Doc.
	 * @param mainObj This is the complete object. With all data in it. Not only the passed column.
	 * @param actualArguments This is an array of elements. Per definition we expect the length of 2 where the first
	 *                        element is the geometry column wrapped in an Value and the second one is the stroke
	 *                        parameter.
	 *
	 *                        Since the passed geometry column is not a simple type like str/int/float etc. it needs
	 *                        special care. We need to unwrap the obtained complex objects and check every one to be
	 *                        geometric type at all.
	 *
	 *                        The stroke parameter can be used right away.
	 * @return the calculated area of the elements.
	 */
	@Override
	public Value evaluate(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments) {
		/*
		  Value is the argument passed to the function.
		  In this case we expect one "object of any type" ([0]) and the stroking parameter as numeric/float ([1]).
		 */
		Value passedObject = actualArguments[0];
		Value strokeParam = actualArguments[1];
		Collection<IomObject> iomObjectCollection = passedObject.getComplexObjects();

		if(iomObjectCollection == null){
			/*
			  We can stop here because geometries are complex types
			  and a non complex type was passed to the function.
		 	*/
			logger.addEvent(
					logger.logInfoMsg(
							"Skip evaluation "+getQualifiedIliName()+" for (geometry="+ passedObject.getType() +")=>Not an Geometry Type!"
					)
			);
			return Value.createSkipEvaluation();
		}
		Iterator<IomObject> iterator = iomObjectCollection.iterator();
		double area = 0.0;
		while (iterator.hasNext()) {
			/*
			  Since we have to expect multiple IomObjects here, we iterate to find
			  fitting samples.
		 	*/
			IomObject iomo = iterator.next();
			for(int i = 0; i < iomo.getattrcount(); i = i + 1){
				/*
					We expect there more then one attribute per object. Means we could have a mixed geometry.
					Something like collection.
				 */
				String attrname = iomo.getattrname(i);
				logger.addEvent(logger.logInfoMsg("Evaluate "+getQualifiedIliName()+"(geometry="+attrname.toUpperCase()+" stroke="+ strokeParam.getValue() +")"));
				logger.addEvent(logger.logInfoMsg(attrname.toUpperCase()+" contains following path:\n "+ unwrapIOMObjectStrucureToString(iomo, 0)));
				if(attrname.equalsIgnoreCase("SURFACE")){
					/*
						We found a simple polygon and let *2JTS do the rest.
					 */
					area = area + calcArea(iomo, Double.parseDouble(strokeParam.getValue()));
				}
				else if(attrname.equalsIgnoreCase("SURFACES")){
					/*
						We found a multipolygon and let *2JTS do the rest. In this place we can pass "0" as srid.
						Because this is what JTS uses when a GeometryFactory is used without blank.
						http://locationtech.github.io/jts/javadoc-1.17.0/org/locationtech/jts/geom/GeometryFactory.html#GeometryFactory--
						Since creating a MultiPolygon by class is deprecated already but still used by iox:
						https://github.com/claeis/iox-ili/blob/master/src/main/java/ch/interlis/iox_j/jts/Iox2jts.java#L453

						It should be corrected in iox library.
					 */
					area = area + calcMultiArea(iomo, Double.parseDouble(strokeParam.getValue()), 0);
				}
				else {
					logger.addEvent(logger.logInfoMsg("Skipping because it was not a relevant geometry type."));
				}
			}
		}
		logger.addEvent(logger.logInfoMsg("Calcualted Area was "+area));
		return new Value(area);
	}

	/**
	 * Function to calculate simple areas in place with *2JTS.
	 *
	 * @param iomo The object which holds the geometry description. It is derived from the passed arguments.
	 * @param strokeParam The configuration how stroking will be done.
	 * @return calculated area, this is 0.0 if no area like geometry was found in the passed iomo tree.
	 */
	public double calcArea(ch.interlis.iom.IomObject iomo, double strokeParam){
		double area = 0.0;
		try {
			Polygon simplePoly = surface2JTS(iomo, strokeParam);
			area = area + simplePoly.getArea();
			return area;
		}
		catch (Iox2jtsException ex) {
			ex.printStackTrace();
			logger.addEvent(logger.logInfoMsg("There was an error with the creation of JTSGeometry instance."));
		}
		return area;
	}

	/**
	 * Function to brute force the nearly arbitrary tree of objects and let do *2JTS the work (try catch).
	 *
	 * Problem: With the test data in the moment no
	 *
	 * @param iomo The object which holds the geometry description. It is derived from the passed arguments.
	 * @param strokeParam The configuration how stroking will be done.
	 * @param srid The SRID which is needed in the moment by *2JTS, but it seems to be not necessary.
	 * @return calculated area, this is 0.0 if no area like geometry was found in the passed iomo tree.
	 */
	public double calcMultiArea(ch.interlis.iom.IomObject iomo, double strokeParam, int srid){
		double area = 0.0;

		try {
			MultiPolygon multiPolyT = multisurface2JTS(iomo, strokeParam, srid);
			logger.addEvent(logger.logInfoMsg("test area: " + multiPolyT.getArea()));
			for(int i = 0; i < iomo.getattrcount(); i = i + 1) {

				if(iomo.getattrvalue(iomo.getattrname(i)) == null){
					/*
						Assume we have a complex object here => Geometric
					 */
					if (iomo.getattrobj(iomo.getattrname(i), i) != null){
						MultiPolygon multiPoly = multisurface2JTS(iomo.getattrobj(iomo.getattrname(i), i), strokeParam, srid);
						area = area + multiPoly.getArea();
						if(area > 0.0){
							/*
								We found a root in the tree which encapsulates an area type consumable by *2JTS
								So we are happy and can exit.
							 */
							logger.addEvent(logger.logInfoMsg("current area: " + area));
							return area;
						}
						if (iomo.getattrobj(iomo.getattrname(i), i).getattrcount() > 0) {
							/*
								No root was found until now which encapsulates an area type consumable by *2JTS
								So we are unhappy and walk deeper down the tree.
							 */
							area = area + calcMultiArea(iomo.getattrobj(iomo.getattrname(i), i), strokeParam, srid);
						}
					}
				}
			}
			return area;
		}
		catch (Iox2jtsException ex) {
			ex.printStackTrace();
			logger.addEvent(logger.logInfoMsg("There was an error with the creation of JTSGeometry instance."));
		}
		return area;
	}

	/**
	 * Pure convenience function to provide easy access to the object hierarchy. Useful for debugging.
	 *
	 * @param iomo The object which will be digged into.
	 * @param level The level of deepnes in the unwrapping process.
	 * @return the path element of current iteration level.
	 */
	public String unwrapIOMObjectStrucureToString(ch.interlis.iom.IomObject iomo, int level){
		String path = "";
		String indent = " ";
		for(int j = 0; j < level; j = j + 1){
			indent = indent + " ";
		}
		for(int i = 0; i < iomo.getattrcount(); i = i + 1) {
			path = path + indent + iomo.getattrname(i) + "(<" + iomo.getobjecttag() + ">)" + "\n";
			if(iomo.getattrvalue(iomo.getattrname(i)) != null){
				path = path + indent + iomo.getattrname(i) + "=" + iomo.getattrvalue(iomo.getattrname(i)) + "\n";
			}
			else{
				if (iomo.getattrobj(iomo.getattrname(i), i) != null){
					if (iomo.getattrobj(iomo.getattrname(i), i).getattrcount() > 0) {
						path = path + indent + unwrapIOMObjectStrucureToString(iomo.getattrobj(iomo.getattrname(i), i), level + 1) + "\n";
					}
				}
			}
		}
		return path;
	}

	/**
	 * POZOR: String directly corresponds to the model it should be used in. This is strange but actual behaviour.
	 * So this whole construct is directly chained to an Interlis model and can't live without it.
	 * Once you have a model which should deal with this, you need to decide how to name your function. Once this is
	 * known you add the string here. Otherwise the magic connection won't work!!!!!!!!
	 *
	 * @return The String of the form <model_name>.<function_name>
	 */
	@Override
	public String getQualifiedIliName() {
		return "IlivalidatorAreaExtension.areaCalculation";
	}
}
