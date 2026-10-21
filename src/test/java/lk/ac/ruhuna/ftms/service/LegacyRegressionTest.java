package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;

/** Maven-discoverable bridge retaining the existing assertion-rich regression runners. */
public class LegacyRegressionTest extends TestCase {
    public void testResultCalculation()throws Exception{ResultServiceTest.main(new String[0]);}
    public void testResultPublication()throws Exception{ResultPublicationTest.main(new String[0]);}
    public void testProfile()throws Exception{ProfileServiceTest.main(new String[0]);}
    public void testProfilePictures()throws Exception{ProfilePictureServiceTest.main(new String[0]);}
    public void testMaterials()throws Exception{CourseMaterialServiceTest.main(new String[0]);}
    public void testNotices()throws Exception{NoticeServiceTest.main(new String[0]);}
}