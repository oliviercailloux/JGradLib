package io.github.oliviercailloux.vexam;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/** NB this creates compile error from Eclipse inner compiler, but not from Eclipse outer compiler:
 * {@literal
1. ERROR in /…/src/main/java/io/github/oliviercailloux/vexam/VariousClass.java (at line 36)
	static int called = 1 ;
	           ^^^^^^
The field called cannot be declared static in a non-static inner type, unless initialized with a constant expression
----------
2. ERROR in /…/src/main/java/io/github/oliviercailloux/vexam/VariousClass.java (at line 37)
	public static String response(int num){
	                     ^^^^^^^^^^^^^^^^^
The method response cannot be declared static; static methods can only be declared in a static or top level type
}
 * 
 */
public class VariousClass implements Various {

	
	ArrayList<Path> allPath = new ArrayList<>();
	@Override
	public void log() {
		Logger logger = LoggerFactory.getLogger(VariousClass.class);
        logger.info("I want to pass the java course");
		
	}

	@Override
	public String bePolite() {
		return "Hello,  everybody!";
	}

	@Override
	public String ones() {

        class Response {
            static int called = 1 ;
            public static String response(int num){
            	StringBuilder sb = new StringBuilder();
            	sb.append("1".repeat(Math.max(0, num)));
            	called++;
            	return sb.toString();
            }

            public static String response(){
                return response(called);
            }

        }
        return Response.response();
    }

	@Override
	public String read(Path source) throws IOException {
		File file = source.toFile();
        BufferedReader br = new BufferedReader(new FileReader(file));
        StringBuilder sb = new StringBuilder();
        String str;
        while ((str = br.readLine()) != null){
            sb.append(str);
        }
        sb.append('\n');
        return sb.toString();
    }
	

	@Override
	public void addPath(Path source) {
        allPath.add(source);
    }

	@Override
	public String readAll() throws IOException {
        StringBuilder sb = new StringBuilder();
        for(Path source : allPath){
            sb.append(read(source));
        }
        return sb.toString();
    }

}


