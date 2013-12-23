package gov.va.vler.pdf.generator.rest.ws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Ignore;
import org.junit.Test;

//@Ignore
public class PDFGeneratorWSTest 
{
	private PdfGeneratorWS testSubject = new PdfGeneratorWS();

	@Test
    public void isSuccessfullyReturnsPDFInputStream()
    {
	    try 
	    {
	    	String url = "http://localhost:7001/pdfgenerator/fs/5269f4eb59e13d6c1a0000ab11";
    	
	    	//Connect to REST
	    	WebClient client = WebClient.create(url);
              
	    	//Get response
	    	Response response = client.get();
	    	
	    	System.out.println("client status " + response.getStatus());
	    	
	    	File pdfFile = new File("/temp/restimage1.pdf");
			IOUtils.copy((InputStream)response.getEntity(), new FileOutputStream(pdfFile));
			
			assertNotNull(pdfFile);
			assertTrue(pdfFile.length() > 0);

			pdfFile.delete();
		} 
	    catch (Exception e) 
	    {
			e.printStackTrace();
		} 	
	}
	
	@Test
    public void isGetsImageFromECRUD()
    {
		try 
		{
			Response response = testSubject.getResponseFromCRUDWebClient("5269f4eb59e13d6c1a0000ab");
			
			assertNotNull(response);
			assertEquals(200, response.getStatus());
			assertNotNull(response.getEntity());
			
			IOUtils.copy((InputStream)response.getEntity(), new FileOutputStream(new File("/temp/ecrudImage.jpg")));
		} 
		catch (Exception e) 
		{
			fail(e.getMessage());
		}
    }

	@Test
    public void isCreatesPDFByteArrayStream()
    {
		try 
		{
			InputStream imgInStream = new FileInputStream(new File("C:/temp/images/hubble.jpg"));
			PDDocument pdfDoc = testSubject.createPDFDocumentFromImage(imgInStream);
			
			assertNotNull(pdfDoc);
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			pdfDoc.save(out);

			File file = new File("/temp/imagePdf.pdf");
			IOUtils.copy(new ByteArrayInputStream(out.toByteArray()), new FileOutputStream(file));
			
			file.delete();
		} 
		catch (Exception e) 
		{
			fail(e.getMessage());
		}
    }
}
