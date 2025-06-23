package kr.co.himatch.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

@Configuration
public class DataLoader {

    private  final VectorStore vectorStore;
    private final JdbcClient jdbcClient;
    // # 0. PDF 경로(resources 아래)
    @Value("classpath:/blind.pdf")
    private Resource pdfResource;

    public DataLoader(VectorStore vectorStore, JdbcClient jdbcClient) {
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
    }

    @PostConstruct
    public void init(){
           Integer count=jdbcClient.sql("select count(*) from vector_store")
                   .query(Integer.class)
                   .single();
           System.out.println("No of Records in the PG Vector Store="+count);
           if(count==0){
               System.out.println("Loading.....");
               // PDF Reader
               PdfDocumentReaderConfig config=PdfDocumentReaderConfig.builder()
                       .withPageTopMargin(0)
                       .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                               .withNumberOfTopTextLinesToDelete(0)
                               .build())
                       .withPagesPerDocument(1)
                       .build();
              // # 1.단계 : 문서로드(Load Documents)
               PagePdfDocumentReader pdfReader =new PagePdfDocumentReader(pdfResource,config);
               List<Document> documents=pdfReader.get();
               // System.out.println(documents.toString()); // 25
               // 1000글자 단위로 자른다.
               // # 2.단계 : 문서분할(Split Documents)
               TokenTextSplitter splitter = new TokenTextSplitter(1000, 400, 10, 5000, true);
               List<Document> splitDocuments=splitter.apply(documents);
//               System.out.println(splitDocuments.size()); // 45
//               System.out.println(splitDocuments.get(0)); // 25
               // # 3.단계 : 임베딩(Embedding) -> 4.단계 : DB에 저장(백터스토어 생성)
               vectorStore.accept(splitDocuments); // OpenAI 임베딩을 거친다.
               System.out.println("Application is ready to Serve the Requests");
           }
    }
}
/*
 Spring AI  VS LangChain
 */