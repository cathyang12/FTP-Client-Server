#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <stdlib.h>
#include <unistd.h>

#include "cdup.h"

/*
Accepts programState, which tells us the current working directory
as well as ftp server directory
chgwdup will stop at ftp server directory

Return values:

0 if succesful
1 otherwise
*/
int cdup(programState *curState)
{
    printf("curState->curWorDir: %s,  curState->noPar: %s\n", curState->curWorDir, curState->noPar);

    //in the root directory already
    if (strcasecmp(curState->curWorDir, curState->noPar) == 0)
    {
        //not allowed to go up
        printf("SERVER: Not allowed to CDUP to server parent directory.\n");
        dprintf(curState->new_fd, "550 Failed to change directory.\n");
        return 1;
    }

    int i = strlen(curState->curWorDir);
    if (curState->curWorDir[i] == '/')
        curState->curWorDir[i] = '\0';

    //find the index for last "/"
    for (i; i > 0; i--)
    {
        if (curState->curWorDir[i] == '/')
        {
            curState->curWorDir[i] = '\0';
            break;
        }
    }
    printf("SERVER: index of backslash:%d\n", i);

    char *temp = malloc(i * (sizeof(char)));
    memcpy(temp, curState->curWorDir, i);
    printf("SERVER: proposed temp addr is %s\n", temp);
    free(curState->curWorDir);
    curState->curWorDir = temp;
    chdir(temp);

    printf("SERVER: CDUP to %s\n", curState->curWorDir);
    //, you are now at %s!  , curState->curWorDir
    dprintf(curState->new_fd, "250 Directory sucessfully changed.\n");
    return 0;
}