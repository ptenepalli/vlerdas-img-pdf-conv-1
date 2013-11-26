package gov.va.vler.pdf.generator.rest.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.BadSecurityHandlerException;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDJpeg;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Path("/fs")
public class PdfGeneratorWS 
{
	@Value("${pdf.generator.ecrud.url}")
	private String ecrudURL;

	@Value("${pdf.generator.temp.file.path}")
	private String pdfFilePath;
	
	@Value("${pdf.generator.qpdf.path}")
	private String qpdfPath;
	
	private static final String UNDER_SCORE = "_";
	private static final String PDF_EXTENSION = ".pdf";
	private static final String TEMP_FILE = UNDER_SCORE + "temp";
	private static final String LINEARIZED_FILE = UNDER_SCORE + "linearized";
	
	private static final Log log = LogFactory.getLog(PdfGeneratorWS.class);

	@GET
	@Path("/{id}")
	@Produces("application/pdf")
	public InputStream getPDFByteArrayStream(@PathParam("id") String id) throws COSVisitorException, IOException, BadSecurityHandlerException, InterruptedException 
	{
		log.info("Get PDF Stream for id: " + id);
		PDDocument pdfDoc = null;
		
		try
		{
			// create PDF Document
			pdfDoc = createPDFDocumentFromImage(getStreamFromCRUDWebClient(id));
	
			// create pdf byte array stream
			return getOptimizedPDF(id, pdfDoc);
		} 
		finally 
		{
			if (pdfDoc != null) 
			{
				pdfDoc.close();
			}
		}
	}

    InputStream getStreamFromCRUDWebClient(String id) throws FileNotFoundException, IOException 
    {    	
    	String url = ecrudURL + id;
    	log.info("url : " + url);
    	
		//Connect to CRUD
        WebClient client = WebClient.create(url);
              
        //Get response
		Response response = client.get();
	        
		if (response.getStatus() == 200 || response.getStatus() == 201)
		{
			return (InputStream)response.getEntity();
		}
		return null;
    }

	PDDocument createPDFDocumentFromImage(InputStream image) throws IOException, BadSecurityHandlerException 
	{
		// create the pdf document
		PDDocument pdfDoc = null;
		
		// create a page
		PDPage page = new PDPage();
		PDRectangle mediaBox = page.findMediaBox();

		// add page to pdf doc
		pdfDoc = new PDDocument();
		pdfDoc.addPage(page);

		// create image from input stream
	   	PDXObjectImage img = new PDJpeg(pdfDoc, image);

		// attributes to scale image dimensions to fit in pdf page
		float leftIndent = 12;
		float topIndent = 12;
		float imgWidth = img.getWidth();
		float imgHeight = img.getHeight();
		float yPos = mediaBox.getHeight() - imgHeight - topIndent;
		float maxWidth = mediaBox.getWidth() - (2 * leftIndent);
		float maxHeight = mediaBox.getHeight() - (2 * topIndent);

		// adjust image width
		if (imgWidth > maxWidth && imgHeight < maxHeight) 
		{
			imgWidth = maxWidth;
		}
		// adjust image height
		else if (imgWidth < maxWidth && imgHeight > maxHeight) 
		{
			imgHeight = maxHeight;
			leftIndent = (mediaBox.getWidth() - imgWidth) / 2;
			yPos = mediaBox.getHeight() - imgHeight - topIndent;
		}
		// adjust no image dimensions
		else if (imgWidth < maxWidth && imgHeight < maxHeight) 
		{
			leftIndent = (mediaBox.getWidth() - imgWidth) / 2;
		}
		// adjust image width and height
		else 
		{
			imgWidth = maxWidth;
			imgHeight = maxHeight;
			yPos = mediaBox.getHeight() - imgHeight - topIndent;
		}
				
		PDPageContentStream contentStream = new PDPageContentStream(pdfDoc,	page);
		contentStream.drawXObject(img, leftIndent, yPos, imgWidth, imgHeight);
		contentStream.close();

		return pdfDoc;
	}

	InputStream getPDF(String id, InputStream image) throws IOException, BadSecurityHandlerException, COSVisitorException, InterruptedException
	{
		PDDocument pdfDoc = createPDFDocumentFromImage(image);
		return getOptimizedPDF(id, pdfDoc);
	}
	
	private InputStream getOptimizedPDF(String id, PDDocument pdfDoc) throws COSVisitorException, IOException, BadSecurityHandlerException, InterruptedException
	{
		File pdfFile = null;
		File linearizedPdfFile = null;

		String filePath = pdfFilePath + id + UNDER_SCORE + System.currentTimeMillis();
		String pdfFileName = filePath + PDF_EXTENSION;
		String tempPdfFileName = filePath + TEMP_FILE + PDF_EXTENSION;
		String linearizedPdfFileName = filePath + LINEARIZED_FILE + PDF_EXTENSION;
		
		try
		{
			pdfFile = new File(pdfFileName);
			pdfDoc.save(pdfFile);
			
			// enable PDF Document Assembly
			StringBuilder docAssemblyCmd = new StringBuilder();
			docAssemblyCmd.append(qpdfPath).append("qpdf --encrypt \"\" \"\" 128 --modify=assembly -- ")
						  .append(pdfFileName).append(" ").append(tempPdfFileName);
			Process docAssemplyProcess = Runtime.getRuntime().exec(docAssemblyCmd.toString());
			docAssemplyProcess.waitFor();
			
			// make PDF linearized i.e. enable Fast Web View
			StringBuilder linearizedCmd = new StringBuilder();
			linearizedCmd.append(qpdfPath).append("qpdf --linearize ")
			             .append(tempPdfFileName).append(" ").append(linearizedPdfFileName);
			Process linearizedProcess = Runtime.getRuntime().exec(linearizedCmd.toString());
			linearizedProcess.waitFor();
			
			linearizedPdfFile = new File(linearizedPdfFileName);
			return (linearizedPdfFile != null && linearizedPdfFile.exists()) ? new FileInputStream(linearizedPdfFile) : null;
		}
		finally
		{
			if (pdfFile != null && pdfFile.exists())
			{
				pdfFile.delete();
			}
			if (linearizedPdfFile != null && linearizedPdfFile.exists())
			{
				linearizedPdfFile.delete();
			}
			File tempPdfFile = new File(tempPdfFileName);
			if (tempPdfFile != null && tempPdfFile.exists())
			{
				tempPdfFile.delete();
			}
		}
	}
}
