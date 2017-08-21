#include "retr.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/socket.h> //for send()
#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>

#define BLOCK_SIZE 4096 //how many will we read at a time? CS313!

//return 0 on success, or some higher number otherwise
int retr(programState *curState)
{
    if (curState->isPasv == false)
    { //no PASV connection
        printf("SERVER: Attempted RETR without PASV.\n");
        dprintf(curState->new_fd, "425 Use PASV first.\n");
        return 1;
    }

    if (curState->comm->argument == NULL)
    { //no filename specified
        printf("SERVER: Failed to open file %s.", curState->comm->argument);
        dprintf(curState->new_fd, "550 Failed to open file.\n");
        close(curState->pas_fd);
        curState->isPasv = false;
        return 2;
    }

    FILE *file_hd = fopen(curState->comm->argument, "rb"); //open read-only binary file
    if (file_hd == NULL)
    { //invalid filename specified/unable to access file
        printf("SERVER: Failed to open file %s.", curState->comm->argument);
        dprintf(curState->new_fd, "550 Failed to open file.\n");
        close(curState->pas_fd);
        curState->isPasv = false;
        return 3;
    }

    dprintf(curState->new_fd, "150 Opening BINARY mode data connection for %s\n", curState->comm->argument);

    //TODO: read the file and dump it through pas_fd
    void *file_buffer = malloc(BLOCK_SIZE);
    size_t read_bytes = BLOCK_SIZE;
    ssize_t write_res = BLOCK_SIZE;
    printf("SERVER: RETR: Beginning to read file %s.\n", curState->comm->argument);
    while (read_bytes == BLOCK_SIZE)
    {
        read_bytes = fread(file_buffer, 1, BLOCK_SIZE, file_hd);
        write_res = write(curState->pas_fd, file_buffer, read_bytes);
        if (write_res == 0)
        { //failed to write at all
            printf("SERVER: Failed to write %s.", curState->comm->argument);
            dprintf(curState->new_fd, "426 Read or transmit error.\n");
            return 4;
        }
        if (write_res < read_bytes)
        { //only wrote a portion
            printf("SERVER: Failed to write %s.", curState->comm->argument);
            dprintf(curState->new_fd, "451 Read or transmit error.\n");
            return 5;
        }
    }

    //why did we stop? EOF or error?
    close(curState->pas_fd);
    curState->isPasv = false;

    if (feof(file_hd))
    {
        //normal file termination, done without incidents
        printf("SERVER: RETR: file sent sucessfully.\n");
        dprintf(curState->new_fd, "226 File send OK.\n");
        fclose(file_hd);
        return 0;
    }
    else
    {
        printf("SERVER: RETR: abnormal file failure!\n");
        dprintf(curState->new_fd, "426 Read error.\n");
        fclose(file_hd);
        return 6;
    }
}