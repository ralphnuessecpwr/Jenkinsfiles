package com.compuware.devops.util

class MailList implements Serializable {

    def Map MailListMap

    MailList()
    {
        MailListMap = ["HDDRXM0":"ralph.nuesse@compuware.com"]
    }

    public Map getEmail(String key)
    {
        return this.MailListMap[(key.toUpperCase())]
    }
}
