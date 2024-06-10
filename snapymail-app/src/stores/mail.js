import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useMailStore = defineStore('mail', {
    state: () => ({
        _loading: false,
        _mails: [],
        _drafts: [],
        _trash: [],
        _sent: [],
        _folders: [],
        _is_read: [],
        _is_deleted: [],
        _selected_folder: "INBOX",
        _selected_mail: null,
        _active_conversation: null,
        _api_root: 'http://localhost:8080/api/emails',
        _my_email: 'opu@broadbrander.com'
    }),
    getters: {
        loading: (state) => state._loading,
        mails: (state) => state._mails,
        drafts: (state) => state._drafts,
        trash: (state) => state._trash,
        folders: (state) => state._folders,
        selected_folder: (state) => state._selected_folder,
        selected_mail: (state) => state._selected_mail,
        is_read: (state) => state._is_read,
        is_deleted: (state) => state._is_deleted,
        mapped_mails: (state) => {
            // map the mails based on sender
            let mapped = {}
            state._mails.forEach(mail => {
                if (!mapped[mail.from]) {
                    mapped[mail.from] = []
                }

                if (mail.from.includes('<')) {
                    mail.name = mail.from.split('<')[0].trim()
                    mail.email = mail.from.split('<')[1].replace('>', '').trim()
                } else {
                    mail.name = mail.from
                    mail.email = mail.from
                }

                if (mail.email === state._my_email) {
                    mail.sent = true
                }

                mapped[mail.from].push(mail)
            })
            return mapped
        },
        active_conversation: (state) => {
            let conversation = []
            if (!state._is_read.includes(state._active_conversation)) {
                state._is_read.push(state._active_conversation)
            }
            state._mails.forEach(mail => {
                if (mail.from === state._active_conversation) {
                    conversation.push(mail)
                }
            })

            state._sent.forEach(mail => {
                if (state._active_conversation.includes(mail.to)) {
                    conversation.push(mail)
                }
            })

            conversation.sort((a, b) => {
                return new Date(a.date).getTime() - new Date(b.date).getTime()
            })

            return conversation
        }
    },
    actions: {
        async fetchMails(folder = 'INBOX') {
            this._loading = true
            await fetch(this._api_root + '/receive?folder=' + folder)
                .then(response => response.json())
                .then(data => {
                    if (!this._mails.length) {
                        this._mails = data
                    } else {
                        data.forEach(mail => {
                            if (!this._mails.find(m => m.messageId === mail.messageId)) {
                                this._mails.push(mail)
                            }
                        });
                    }
                    this._loading = false

                    // fetch mail data for each mail in background
                    this._mails.forEach(async mail => {
                        // if deleted, skip and remove from the list
                        if (this._is_deleted.includes(mail.messageId)) {
                            this._mails = this._mails.filter(m => m.messageId !== mail.messageId)
                            return
                        }

                        // date of _mails is in this format: Mon Jun 10 02:41:05 BDT 2024
                        // new Date() cannot parse this format, so we need to convert it to a parsable format
                        let date = mail.date.split(' ')
                        let new_date = date[1] + ' ' + date[2] + ' ' + date[5] + ' ' + date[3]
                        try {
                            date = new Date(new_date)?.toISOString()

                            mail.date = date
                        } catch (e) {
                        }


                        if (this._is_read.includes(mail.messageId)) {
                            mail.read = true
                        } else {
                            mail.read = false
                        }
                        this.fetchMail(mail.messageId, true).then(response => response.json()).then(data => {
                            mail.raw = data
                            mail.sent = false
                        });
                    })
                })
        },
        async fetchSent() {
            this._loading = true
            await fetch(this._api_root + '/receive?folder=Sent')
                .then(response => response.json())
                .then(data => {
                    data.forEach(mail => {
                        mail.sent = true
                        this._sent.push(mail)
                    });
                    this._loading = false
                })
        },
        async fetchFolders() {
            this._loading = true
            await fetch(this._api_root + '/folders')
                .then(response => response.text())
                .then(data => {
                    this._folders = data.split("\n")
                    this._loading = false
                })
        },
        async fetchMail(id, silent = false) {
            if (!silent) {
                this._loading = true
                await fetch(this._api_root + '/read?messageId=' + id)
                    .then(response => response.json())
                    .then(data => {
                        this._selected_mail = data
                        this._loading = false
                    })
            } else {
                return await fetch(this._api_root + '/read?messageId=' + id);
            }
        },
        async sendMail(to, subject, body) {
            this._loading = true
            // url encode the mail body
            body = encodeURIComponent(body)
            subject = encodeURIComponent(subject)
            this._loading = false
            return await fetch(this._api_root + '/send?to=' + to + '&subject=' + subject + '&body=' + body, {
                method: 'POST'
            })
        },
        async deleteMail(id) {
            return await fetch(this._api_root + '/delete?messageId=' + id, {
                method: 'DELETE'
            })
        },
        async forwardMail(id, to) {
            return await fetch(this._api_root + '/forward?messageId=' + id + '&to=' + to, {
                method: 'POST'
            })
        },
    },
    persist: true,
})
