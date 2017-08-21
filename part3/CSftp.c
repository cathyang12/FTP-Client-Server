#include <sys/types.h>
#include <sys/socket.h>
#include <stdio.h>
#include <ctype.h>

//from Beej's guide
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/wait.h>
#include <signal.h>

#include "common.h"
#include "dir.h"
#include "usage.h"
#include "cdup.h"
#include "pasv.h"
#include "cwd.h"
#include "retr.h"

#define MAXDATASIZE 512 // maximum size (in bytes) of incoming data buffer

/*
*Split string input into separate words using " " as separator 
*after-effects:
*int returned is the command (or -1 if invalid)
*buf will just be the argument (if any)
*/
userCommand *splitCommand(char *buf)
{
    //shortest command is 3 letters
    userCommand *out = malloc(sizeof(userCommand));

    out->command = -1;

    //strip off trailing \n \r
    char *bufProccessed = strtok(buf, "\n\r\0");

    //get the command
    char *com = strtok(bufProccessed, " ");
    //change buf to argument only
    out->argument = strtok(NULL, " \n\0");

    if (com == NULL)
        return out;
    if ((strcasecmp(com, "CWD\0") == 0))
        out->command = CWD;
    if ((strcasecmp(com, "DIR\0") == 0))
        out->command = DIR;
    if ((strcasecmp(com, "USER\0") == 0))
        out->command = USER;
    if ((strcasecmp(com, "QUIT\0") == 0))
        out->command = QUIT;
    if ((strcasecmp(com, "CDUP\0") == 0))
        out->command = CDUP;
    if ((strcasecmp(com, "TYPE\0") == 0))
        out->command = TYPE;
    if ((strcasecmp(com, "MODE\0") == 0))
        out->command = MODE;
    if ((strcasecmp(com, "STRU\0") == 0))
        out->command = STRU;
    if ((strcasecmp(com, "RETR\0") == 0))
        out->command = RETR;
    if ((strcasecmp(com, "PASV\0") == 0))
        out->command = PASV;
    if ((strcasecmp(com, "NLST\0") == 0))
        out->command = NLST;
    return out;
}

/*
* execute the command; will need to refactor for shrink the size
*/
char *execute(programState *curState)
{
    //printf("SERVER: argument %s\n", curState->comm->argument);

    switch (curState->comm->command)
    {
    case MODE:
        if (strcmp(curState->comm->argument, "S") == 0)
        {
            printf("SERVER: MODE is set to Stream.\n");
            dprintf(curState->new_fd, "200 MODE set to Stream.\n");
        }
        else
        {
            printf("SERVER: MODE only support Stream type.\n");
            dprintf(curState->new_fd, "504 Bad MODE command\n");
        }
        break;
    case TYPE:
        if (strcmp(curState->comm->argument, "A") == 0)
        {
            printf("SERVER: Switching to ASCII mode.\n");
            dprintf(curState->new_fd, "200 Switching to ASCII mode.\n");
            //TYPE=A and TYPE I;
        }
        else if (strcmp(curState->comm->argument, "I") == 0)
        {
            printf("SERVER: Switching to Image mode.\n");
            dprintf(curState->new_fd, "200 Switching to Image mode.\n");
        }
        else
        {
            printf("SERVER: Unrecognised TYPE command.\n");
            dprintf(curState->new_fd, "500 Unrecognised TYPE command.\n");
        }
        break;
    case STRU:
        if (strcmp(curState->comm->argument, "F") == 0)
        {
            printf("SERVER: Switching to File structure type.\n");
            dprintf(curState->new_fd, "200 Switching to File structure type.\n");
        }
        else
        {
            printf("SERVER: Unrecognised TYPE command.\n");
            dprintf(curState->new_fd, "500 Unrecognised STRU command.\n");
        }
        break;
    case CWD:
        cwd(curState);
        break;
    case CDUP:
        cdup(curState);
        break;
    case RETR:
        retr(curState);
        break;
    case PASV:
        if (curState->isPasv)
        {
            // already in passive mode, close old port
            close(curState->pas_fd);
        }
        curState->pas_fd = pasv(curState);

        if (curState->pas_fd < 0)
        {
            printf("SERVER: Fail to establish passive connection.\n");
        }
        else
        {
            curState->isPasv = true;
            printf("SERVER: Passive connection created.\n");
        }
        break;
    case NLST:
        // NLST has parameter - not allowed
        if (curState->comm->argument)
        {
            printf("SERVER: Attempted NLST with parameter.\n");
            dprintf(curState->new_fd, "501 NLST only supports no parameter.\n");
            //passive mode is not on
        }
        else if (curState->isPasv == false)
        {
            printf("SERVER: Attempted NLST without PASV.\n");
            dprintf(curState->new_fd, "425 Use PASV first.\n");
        }
        else
        {
            printf("current working directory %s\n", curState->curWorDir);
            dprintf(curState->new_fd, "150 Here comes the directory listing.\n");
            listFiles(curState->pas_fd, curState->curWorDir);
            dprintf(curState->new_fd, "226 Directory send OK.\n");
            close(curState->pas_fd);
            curState->isPasv = false;
        }
        break;
    default:
        dprintf(curState->new_fd, "500 Unknown command.\n");
        break;
    }
}

/*  test methodology:
    two ssh sessions, must be connected to same server (thetis?)
    one to run server, one as client
    server: ./CSftp <port>
    client: nc localhost <port>
    type in client session, and server session will react
    */
int main(int argc, char **argv)
{
    // Check the command line arguments
    if (argc != 2)
    {
        usage(argv[0]);
        return -1;
    }
    int intPort = atoi(argv[1]);
    //check if valid port number
    if (intPort < 1024 || 49151 < intPort)
    {
        printf("Invalid port: please select port from 1024 to 49151\n");
        exit(1);
    }

    //create listening socket (done once per program execution)
    int control_socket = createListenSocket(argv[1]);
    if (control_socket == -1)
    {
        fprintf(stderr, "server: failed to bind because port in use.\n");
        exit(1);
    }

    printf("SERVER: Server ready, waiting for connections...\n");

    struct sockaddr_storage their_addr; // connector's address information
    socklen_t sin_size = sizeof their_addr;
    char buf[MAXDATASIZE]; // stores most recently received data

    programState *curState = malloc(sizeof(programState));
    curState->isLogin = false;
    curState->comm = NULL;
    curState->new_fd = 1;
    curState->noPar = getcwd(NULL, 0);
    curState->curWorDir = malloc(strlen(curState->noPar));
    memcpy(curState->curWorDir, curState->noPar, strlen(curState->noPar));
    curState->isPasv = false;
    curState->pas_fd = 0;

    //printf("SERVER: Current directory is %s\n", curState->noPar);

    while (1)
    {
        //infinite time loop (server will run forever)
        sin_size = sizeof their_addr;
        curState->new_fd = accept(control_socket, (struct sockaddr *)&their_addr, &sin_size);
        //error
        if (curState->new_fd == -1)
        {
            perror("accept");
        }
        else
        {
            //print out client IP address
            char s[INET6_ADDRSTRLEN];
            inet_ntop(their_addr.ss_family, get_in_addr((struct sockaddr *)&their_addr), s, sizeof s);

            //Setup from zero
            printf("SERVER: got connection from %s\n", s);
            dprintf(curState->new_fd, "220 Welcome to the danger zone!\n");
            curState->isLogin = false;
            curState->curWorDir = malloc(strlen(curState->noPar));
            memcpy(curState->curWorDir, curState->noPar, strlen(curState->noPar));
            curState->comm = NULL;
            while (1)
            {
                //unprivileged loop
                //client stays here until USER cs317
                //or QUIT, in which case wait for another connection

                memset(buf, 0, MAXDATASIZE - 1);
                //Wait for as much data as possible
                recv(curState->new_fd, buf, MAXDATASIZE - 1, 0);

                if (curState->comm != NULL)
                {
                    free(curState->comm);
                    curState->comm = NULL;
                }

                curState->comm = splitCommand(buf);
                if (curState->comm->command > 8)
                { //either USER or QUIT which are fine
                    if (curState->comm->command == USER)
                    {
                        if (strcmp(curState->comm->argument, "cs317") == 0)
                        {
                            printf("SERVER: 230 Login successful.\n");
                            dprintf(curState->new_fd, "230 Login successful.\n");
                            curState->isLogin = true;
                            break;
                        }
                        else
                        {
                            printf("SERVER: 530 Incorrect username.\n");
                            dprintf(curState->new_fd, "530 User not found.\n");
                        }
                    }
                    else if (curState->comm->command == QUIT)
                    {
                        //destroy this socket and wait for new client
                        printf("SERVER: Current session ended.\n");
                        dprintf(curState->new_fd, "221 Make like a Canadian and leave.\n");
                        break;
                    }
                    else
                    { //non-permissible command
                        printf("SERVER: Command not allowed.\n");
                        dprintf(curState->new_fd, "530 Please login with USER.\n");
                    }
                }
                else
                { //non-permissible command
                    printf("SERVER: Command not allowed.\n");
                    dprintf(curState->new_fd, "530 Please login with USER.\n");
                }
            }

            //user logged in
            if (curState->isLogin)
            {
                printf("SERVER: Successful authentication!\n");
            }
            else
            {
                //user quit, can accept another client
                printf("SERVER: User QUIT.\n");
                close(curState->new_fd);
                continue;
            }
            while (1)
            {
                //privileged loop
                //user has logged in
                //run until QUIT, dropped connection, apocalypse etc

                memset(buf, 0, MAXDATASIZE - 1);
                //Wait for as much data as possible
                recv(curState->new_fd, buf, MAXDATASIZE - 1, 0);

                if (curState->comm != NULL)
                {
                    free(curState->comm);
                    curState->comm = NULL;
                }

                curState->comm = splitCommand(buf);
                switch (curState->comm->command)
                {
                case USER:
                    printf("SERVER: 500 Already logged in.\n");
                    dprintf(curState->new_fd, "500 Already logged in as cs317.\n");
                    break;
                case QUIT:
                    printf("SERVER: Current session ended.\n");
                    dprintf(curState->new_fd, "221 Make like a Canadian and leave.\n");
                    curState->isLogin = false;
                    break;
                default:
                    execute(curState);
                    break;
                }
                if (!curState->isLogin)
                {
                    printf("SERVER: User QUIT.\n");
                    break;
                }
            }
            curState->isLogin = false;

            if (curState->comm != NULL)
            {
                free(curState->comm);
                curState->comm = NULL;
            }

            close(curState->new_fd);
        }
    }

    //cleanup, if any
    free(curState->noPar);
    if (curState->comm != NULL)
    {
        free(curState->comm);
        curState->comm = NULL;
    }
    free(curState);
}

/*
general flow:
validate selected port
find CWD and store
start listening on that port
when user connect respond with standard greeting (220)
user is unauthenticated (boolean?) so check if user is allowed whatever command received
permitted is user, quit
once authenticated move to a similar listen loop but without checking if authenticated/permissible commands

each function in its own file (.c/.h) except QUIT
when pasv must open up a second port and listen there
*/