
package com.flowforge.etl.connectors.sftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface SftpFileOperations {

    InputStream read(String remotePath) throws IOException;

    OutputStream write(String remotePath) throws IOException;
}
