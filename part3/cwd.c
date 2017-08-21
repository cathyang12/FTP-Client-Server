#include <sys/types.h>
#include <sys/socket.h>
#include <ifaddrs.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>

#include "cwd.h"

int cwd(programState *curState)
{
    char *sDot;
    char *dDot;
    char *absPath;
    sDot = strstr(curState->comm->argument, "./");
    dDot = strstr(curState->comm->argument, "../");

    // check if the argument contains ./ or ../
    if (sDot || dDot)
    {
        printf("SERVER: CWD with ./ or ../ not permissible.\n");
        dprintf(curState->new_fd, "550 Failed to change directory. Path cannot contain ../ or ./\n");
    }
    else
    {
        int ret;
        //absolute path
        absPath = strstr(curState->comm->argument, curState->noPar);
        printf("argument addr: %s\n", curState->comm->argument);
        printf("root addr: %s\n", curState->noPar);

        //if argument is / and the root directory is not /,
        if ((strcmp(curState->comm->argument, "/") == 0) && (strcmp(curState->noPar, "/") != 0))
        {
            ret = -1;

            //if it is absolute path
        }
        else if (absPath)
        {
            ret = chdir(curState->comm->argument);
            if (ret == 0)
            {
                char *direct = malloc(strlen(curState->comm->argument));
                memcpy(direct, curState->comm->argument, strlen(curState->comm->argument));
                free(curState->curWorDir);
                curState->curWorDir = direct;
                printf("SERVER: Current path:%s\n", curState->curWorDir);
            }
        }
        else
        {
            //relative path, append the argument
            char *result = malloc(strlen(curState->curWorDir) + strlen(curState->comm->argument) + 2); //+1 for the zero-terminator and "/"
            strcpy(result, curState->curWorDir);
            strcat(result, "/");
            strcat(result, curState->comm->argument);

            printf("SERVER: Relative path: %s\n", result);
            printf("SERVER: Root directory: %s\n", curState->noPar);
            ret = chdir(result);
            if (ret == 0)
            {
                free(curState->curWorDir);
                curState->curWorDir = result;
            }
        }

        if (ret == 0)
        {
            printf("SERVER: Directory successfully changed.\n");
            dprintf(curState->new_fd, "250 Directory successfully changed.\n");
        }
        else
        {
            printf("SERVER: Failed to change directory. \n");
            dprintf(curState->new_fd, "550 Failed to change directory.\n");
        }
    }
}