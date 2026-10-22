package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.*;

/** Checks every academic FXML action against its real controller without launching the toolkit. */
public class AcademicFxmlTest extends TestCase {
    public void testAcademicHandlerMappings()throws Exception{
        for(String name:List.of("marks_entry","assessments","eligibility","results","gpa","reports","lecturer_dashboard","student_dashboard")){
            var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            var document=factory.newDocumentBuilder().parse(getClass().getResourceAsStream("/fxml/"+name+".fxml"));
            var controller=Class.forName(document.getDocumentElement().getAttribute("fx:controller"));
            Set<String> methods=new HashSet<>();for(var method:controller.getDeclaredMethods())methods.add(method.getName());
            var nodes=document.getElementsByTagName("*");
            for(int i=0;i<nodes.getLength();i++){
                var node=(org.w3c.dom.Element)nodes.item(i);String action=node.getAttribute("onAction");
                if(action.startsWith("#"))assertTrue(name+" missing "+action,methods.contains(action.substring(1)));
            }
        }
    }
}
