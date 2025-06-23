package kr.co.himatch.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

@Configuration // 이 클래스가 Spring의 설정 클래스임을 선언합니다.
public class DataLoader {

    private final VectorStore vectorStore; // 문서 임베딩을 저장하고 검색하는 벡터 저장소
    private final JdbcClient jdbcClient; // 데이터베이스와 상호작용하기 위한 JDBC 클라이언트

    // # 0. PDF 경로(resources 아래)
    // "classpath:/blind.pdf" 경로에 있는 PDF 파일을 리소스로 주입받습니다.
    @Value("classpath:/blind.pdf")
    private Resource pdfResource;

    // DataLoader 생성자: Spring에 의해 VectorStore와 JdbcClient 빈이 주입됩니다.
    public DataLoader(VectorStore vectorStore, JdbcClient jdbcClient) {
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
    }

    // @PostConstruct: 이 메서드는 빈이 초기화된 후 (의존성 주입이 완료된 후) 한 번만 실행됩니다.
    @PostConstruct
    public void init(){
        // "vector_store" 테이블에 저장된 레코드 수를 조회합니다.
        Integer count = jdbcClient.sql("select count(*) from vector_store")
                .query(Integer.class) // 쿼리 결과를 Integer 타입으로 매핑합니다.
                .single(); // 단일 결과를 가져옵니다.
        System.out.println("No of Records in the PG Vector Store=" + count);

        // 만약 벡터 저장소에 데이터가 없다면 (count가 0이라면)
        if(count == 0){
            System.out.println("Loading....."); // 데이터 로딩 시작 메시지 출력

            // PDF 문서 리더 설정을 구성합니다.
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0) // 페이지 상단 여백을 0으로 설정 (텍스트 추출 시 영향을 미칠 수 있음)
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                            .withNumberOfTopTextLinesToDelete(0) // 추출된 텍스트에서 상단에서 삭제할 줄 수를 0으로 설정 (모든 텍스트 유지)
                            .build())
                    .withPagesPerDocument(1) // 각 문서를 1페이지 단위로 처리하도록 설정합니다.
                    .build();

            // # 1.단계 : 문서 로드(Load Documents)
            // PDF 리더를 초기화합니다. (지정된 PDF 리소스와 설정 사용)
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource, config);
            // PDF 문서에서 페이지 단위로 문서를 읽어 List<Document> 형태로 가져옵니다.
            List<Document> documents = pdfReader.get();

            // 1000글자 단위로 자른다.
            // # 2.단계 : 문서 분할(Split Documents)
            // 텍스트 스플리터를 생성합니다.
            // chunkSize: 각 청크의 최대 토큰 수 (여기서는 1000 토큰)
            // overlap: 이전 청크와 겹치는 토큰 수 (여기서는 400 토큰)
            // minChunkSize: 최소 청크 토큰 수 (여기서는 10 토큰)
            // maxChunkSize: 최대 청크 토큰 수 (여기서는 5000 토큰)
            // trimChunks: 청크의 공백을 제거할지 여부 (여기서는 true)
            TokenTextSplitter splitter = new TokenTextSplitter(1000, 400, 10, 5000, true);
            // 로드된 문서를 분할하여 List<Document> 형태로 가져옵니다.
            List<Document> splitDocuments = splitter.apply(documents);

            // # 3.단계 : 임베딩(Embedding) -> 4.단계 : DB에 저장(백터스토어 생성)
            // 분할된 문서를 벡터 저장소에 저장합니다.
            // 이 과정에서 Spring AI는 구성된 임베딩 모델 (예: OpenAI 임베딩)을 사용하여 각 문서를 임베딩하고,
            // 그 임베딩 벡터와 함께 원본 문서를 벡터 저장소 (PostgreSQL의 PGVector)에 저장합니다.
            vectorStore.accept(splitDocuments); // OpenAI 임베딩을 거친다.
            System.out.println("Application is ready to Serve the Requests"); // 데이터 로딩 완료 메시지 출력
        }
    }
}
/*
 주요 단계는 다음과 같습니다
 1. 문서 로드 (Load Documents): PDF 파일을 읽어 Document 객체로 변환합니다.
 2. 문서 분할 (Split Documents): 긴 문서를 작은 청크(chunk)로 분할하여 임베딩 및 검색 효율을 높입니다.
 3. 임베딩 및 저장 (Embedding & Store): 분할된 각 청크를 임베딩 모델을 통해 벡터로 변환하고, 이 벡터와 함께 원본 청크를 벡터 데이터베이스(여기서는 PGVector)에 저장합니다.
 */