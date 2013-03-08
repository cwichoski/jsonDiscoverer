package fr.inria.atlanmod.discoverer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import coverage.ConceptMapping;
import coverage.Coverage;
import coverage.CoverageFactory;
import coverage.util.CoverageCreator;

import fr.inria.atlanmod.JsonStandaloneSetup;

public class JsonComposer {
	public static String TEST_FILE_1 = "C:/Users/useradm/eclipses/eclipse-juno/runtime-JSON/Test/tan1/tan1.ecore";
	public static String TEST_FILE_2 = "C:/Users/useradm/eclipses/eclipse-juno/runtime-JSON/Test/tan2.ecore";
	public static String RESULT = "C:/Users/useradm/eclipses/eclipse-juno/runtime-JSON/Test/tan-composed.ecore";

	private List<File> files;
	private HashMap<String, EClass> registry;

	public static void main(String[] args) {
		JsonStandaloneSetup.doSetup();

		ArrayList<File> fileList = new ArrayList<File>();
		fileList.add(new File(TEST_FILE_1));
		fileList.add(new File(TEST_FILE_2));
		JsonComposer composer = new JsonComposer(fileList);
		composer.compose(new File(RESULT));
	}

	public JsonComposer(List<File> files) {
		this.files = files;
	}

	public void compose(File resultPath) {
		if(files.size() == 0) 
			return;

		EPackage finalPackage = EcoreFactory.eINSTANCE.createEPackage();
		finalPackage.setName("composed");
		finalPackage.setNsPrefix("composed");
		finalPackage.setNsURI("http://composed");

		registry = new HashMap<String, EClass>();
		List<EReference> referencesToCheck = new ArrayList<EReference>();
		List<CoverageCreator> coverageCreators = new ArrayList<CoverageCreator>();
		for(File file: files) {
			CoverageCreator coverageCreator = new CoverageCreator(file);
			
			EPackage singlePackage = loadEPackage(file);
			for(EClassifier classifier : singlePackage.getEClassifiers()) {
				if (classifier instanceof EClass) {
					EClass eClass = (EClass) classifier;
					EClass registryElement = registry.get(eClass.getName());
					if(registryElement == null) {
						EClass duplicatedEClass = duplicateEClass(eClass);
						registry.put(eClass.getName(), duplicatedEClass);
						eClass = duplicatedEClass;
						coverageCreator.createConceptMapping(eClass, duplicatedEClass);
					} else {
						composeAttributes(registryElement, eClass, coverageCreator);
					}
					for(EStructuralFeature otherFeature : eClass.getEStructuralFeatures()) 
						if (otherFeature instanceof EReference) 
							referencesToCheck.add((EReference) otherFeature);

				}
			}
			
			coverageCreators.add(coverageCreator);
		}


		for(EClass eClass : registry.values()) {
			finalPackage.getEClassifiers().add(eClass);
		}

		EClass unknown = EcoreFactory.eINSTANCE.createEClass();
		unknown.setName("Unknown");
		boolean unknownUsed = false;

		for(EReference reference : referencesToCheck) {
			EClassifier referredEClassifier = reference.getEType();
			if (referredEClassifier instanceof EClass) {
				EClass referredEClass = (EClass) referredEClassifier;
				EClass registryElement = registry.get(referredEClass.getName());
				if(registryElement == null) {
					reference.setEType(unknown);
					System.out.println("Reference " + reference.getName() + " with unknown type");
					unknownUsed = true;
				} else {
					reference.setEType(registryElement);
					System.out.println("Reference " + reference.getName() + " re-assigned");
				}
			}
		}
		
		if(unknownUsed) {
			finalPackage.getEClassifiers().add(unknown);
		}
		

		saveEPackage(finalPackage, resultPath);
		
		for(CoverageCreator coverageCreator : coverageCreators) 
			coverageCreator.save(resultPath);

	}

	private void composeAttributes(EClass existingClass, EClass otherClass, CoverageCreator coverageCreator) {
		for(EStructuralFeature otherFeature : otherClass.getEStructuralFeatures()) {
			if (otherFeature instanceof EAttribute) {
				EAttribute otherAttribute = (EAttribute) otherFeature;
				EStructuralFeature existingFeature = existingClass.getEStructuralFeature(otherAttribute.getName());
				if(existingFeature == null) {
					EAttribute newAttribute = duplicateAttribute(otherAttribute);
					existingClass.getEStructuralFeatures().add(newAttribute);
					System.out.println("Attribute " + newAttribute.getName() + " added");
					existingFeature = newAttribute;
				} else {
					existingFeature.setEType(EcorePackage.Literals.ESTRING);
					System.out.println("Attribute " + existingFeature.getName() + " refined to String");
				}				
				coverageCreator.createAttMapping(otherAttribute, (EAttribute) existingFeature);
			}
		}

	}

	private EAttribute duplicateAttribute(EAttribute otherAttribute) {
		EAttribute newAttribute = EcoreFactory.eINSTANCE.createEAttribute();
		newAttribute.setName(otherAttribute.getName());
		newAttribute.setEType(otherAttribute.getEType());
		newAttribute.setUpperBound(otherAttribute.getUpperBound());
		newAttribute.setLowerBound(otherAttribute.getLowerBound());
		return newAttribute;
	}
	
	private EReference duplicateReference(EReference otherReference) {
		EReference newReference = EcoreFactory.eINSTANCE.createEReference();
		newReference.setName(otherReference.getName());
		newReference.setEType(otherReference.getEType());
		newReference.setUpperBound(otherReference.getUpperBound());
		newReference.setLowerBound(otherReference.getLowerBound());
		return newReference;		
	}
	
	private EClass duplicateEClass(EClass otherClass) {
		EClass newClass = EcoreFactory.eINSTANCE.createEClass();
		newClass.setName(otherClass.getName());
		newClass.setAbstract(otherClass.isAbstract());
		
		for(EStructuralFeature otherFeature : otherClass.getEStructuralFeatures()) {
			if (otherFeature instanceof EReference) {
				EReference duplicatedReference = duplicateReference((EReference) otherFeature);
				newClass.getEStructuralFeatures().add(duplicatedReference);
			} else if (otherFeature instanceof EAttribute) {
				EAttribute duplicatedAttribute = duplicateAttribute((EAttribute) otherFeature);
				newClass.getEStructuralFeatures().add(duplicatedAttribute);
			}
		}
		return newClass;
	}

	private EPackage loadEPackage(File file) {
		ResourceSet rset = new ResourceSetImpl();
		Resource res = rset.getResource(URI.createFileURI(file.getAbsolutePath()), true);

		try {
			res.load(null);
		} catch (IOException e) {
			e.printStackTrace();
		}

		EPackage ePackage = (EPackage) res.getContents().get(0);
		return ePackage;
	}

	private void saveEPackage(EPackage ePackage, File resultPath) {
		ResourceSet rset = new ResourceSetImpl();
		Resource res = rset.createResource(URI.createFileURI(resultPath.getAbsolutePath()));

		try {
			res.getContents().add(ePackage);
			res.save(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}